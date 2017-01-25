package TUDITPM.Kafka;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;
import java.util.logging.Level;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bson.Document;
import org.json.JSONObject;

import TUDITPM.Kafka.DBConnectors.MongoDBConnector;
import TUDITPM.Kafka.Loading.LegalFormHelper;
import TUDITPM.Kafka.Loading.PropertyFile;
import TUDITPM.Kafka.Loading.PropertyLoader;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

/**
 * Gets RSS and atOM feeds into a Kafka producer. a single feed of the specified
 * type.
 * 
 * @author Christian Zendo
 * @author Tobias Mahncke
 * @version 5.0
 */
public class ProducerRSSatOM extends Thread {
	private String dbname;

	public ProducerRSSatOM(String dbname) {
		this.dbname = dbname;
	}

	@Override
	public void run() {
		LoggingWrapper.log(this.getClass().getName(), Level.INFO,
				"Thread started");

		MongoDBConnector mongo = new MongoDBConnector(dbname);
		MongoDBConnector config = new MongoDBConnector(
				PropertyLoader.getPropertyValue(PropertyFile.database,
						"config.name"));

		HashSet<String> visited = new HashSet<>();

		for (Document doc : mongo.getCollection("rss").find()) {
			visited.add(doc.getString("link"));
		}

		// set configs for kafka
		Properties props = new Properties();
		props.put("bootstrap.servers", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "bootstrap.servers"));
		props.put("acks",
				PropertyLoader.getPropertyValue(PropertyFile.kafka, "acks"));
		props.put("retries", Integer.parseInt(PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "retries")));
		props.put("batch.size", Integer.parseInt(PropertyLoader
				.getPropertyValue(PropertyFile.kafka, "batch.size")));
		props.put("linger.ms", Integer.parseInt(PropertyLoader
				.getPropertyValue(PropertyFile.kafka, "linger.ms")));
		props.put("buffer.memory", Integer.parseInt(PropertyLoader
				.getPropertyValue(PropertyFile.kafka, "buffer.memory")));
		props.put("key.serializer", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "key.serializer"));
		props.put("value.serializer", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "value.serializer"));

		// Create the producer
		Producer<String, String> producer = null;

		LinkedList<String> companiesWithLegalForms = new LinkedList<>();
		for (Document doc : config.getCollection("companies").find()) {
			companiesWithLegalForms.add(doc.getString("company"));
		}
		
		LinkedList<String> legalForms = PropertyLoader.getLegalForms();
		LinkedList<String[]> companies = LegalFormHelper.removeLegalForms(
				companiesWithLegalForms, legalForms);

		Solr solr = new Solr();

		ArrayList<String> allFeeds = loadFeedSources();

		try {
			producer = new KafkaProducer<>(props);

			for (int i = 0; i < allFeeds.size(); i++) {
				LoggingWrapper.log(this.getClass().getName(), Level.INFO,
						"Reading RSS: " + allFeeds.get(i));
				SyndFeedInput input = new SyndFeedInput();
				SyndFeed feed = null;
				try {
					feed = input.build(new XmlReader(new URL(allFeeds.get(i))));
				} catch (IOException e) {
					LoggingWrapper.log(getName(), Level.WARNING,
							"Server returned HTTP response code: 403 for URL: "
									+ allFeeds.get(i)
									+ ", continuing with next url");
					continue;
				}
				int found = 0;
				int skipped = 0;

				for (SyndEntry entry : feed.getEntries()) {
					String title = entry.getTitle();
					String link = entry.getLink();
					if (!visited.contains(link)
							&& entry.getDescription() != null) {
						found++;
						String text = entry.getDescription().getValue();
						String id = solr.add(title + " " + text);

						// Checked here because of performance
						if ((text.trim().equals("") || text == null)
								&& (title.trim().equals("") || title == null)) {
							solr.delete(id);
							break;
						} else if (text.trim().equals("") || text == null)
							text = title;

						JSONObject json = new JSONObject();
						boolean companyFound = false;
						for (String[] company : companies) {
							if (solr.search("\"" + company[1] + "\"", id)) {
								companyFound = true;
								json.put("companyStripped", company[1]);
								json.put("company", company[0]);
								json.put("source", "rss");
								json.put("link", link);
								json.put("title", title);
								json.put("text", text);
								json.put("id", id);
								if (entry.getPublishedDate() != null) {
									json.put("date", entry.getPublishedDate());
								} else {
									json.put("date", new Date().toString());
								}
								LoggingWrapper.log(this.getClass().getName(),
										Level.INFO, json.toString());

								producer.send(new ProducerRecord<String, String>(
										"rss", json.toString()));
								System.out.println(json);
							}
						}
						if (!companyFound) {
							solr.delete(id);
						}
						visited.add(link);
						mongo.writeToDb(new Document("link", link), "rss");
					} else {
						skipped++;
					}
				}
				LoggingWrapper.log(this.getClass().getName(), Level.INFO,
						"Scanned " + found + " entries, skipped " + skipped
								+ " entries");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private ArrayList<String> loadFeedSources() {
		ArrayList<String> l = new ArrayList<>();
		try {
			FileInputStream in = new FileInputStream(new File(
					"properties/feedsources"));
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String line = null;
			while ((line = br.readLine()) != null) {
				l.add(line);
			}
			br.close();
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Loaded " + l.size() + " feed sources.");

		return l;
	}
}