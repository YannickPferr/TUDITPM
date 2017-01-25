package TUDITPM.Kafka;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Properties;
import java.util.logging.Level;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bson.Document;
import org.json.JSONException;
import org.json.JSONObject;

import TUDITPM.Kafka.DBConnectors.MongoDBConnector;
import TUDITPM.Kafka.DBConnectors.RedisConnector;
import TUDITPM.Kafka.Loading.PropertyFile;
import TUDITPM.Kafka.Loading.PropertyLoader;

/**
 * Listening to the twitter Stream and converting the given data to stream it to
 * spark.
 * 
 * @author Yannick Pferr
 * @author Tobias Mahncke
 * @version 1.2
 */
public class Consumer extends Thread {

	private String dbname;
	private final int PROXIMITY = Integer.parseInt(PropertyLoader
			.getPropertyValue(PropertyFile.solr, "proximity"));

	/**
	 * Creates a new consumer for the given database name.
	 * 
	 * @param dbname
	 *            - the name of the database to which this consumer connects
	 */
	public Consumer(String dbname) {
		this.dbname = dbname;
	}

	/**
	 * Gets called on start of the Thread
	 */
	@Override
	public void run() {
		LoggingWrapper.log(this.getClass().getName(), Level.INFO,
				"Thread started");

		Properties props = new Properties();
		props.put("bootstrap.servers", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "bootstrap.servers"));
		props.put("group.id", "group-2");
		props.put("enable.auto.commit", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "enable.auto.commit"));
		props.put("auto.commit.interval.ms", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "auto.commit.interval.ms"));
		props.put("auto.offset.reset", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "auto.offset.reset"));
		props.put("session.timeout.ms", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "session.timeout.ms"));
		props.put("key.deserializer", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "key.deserializer"));
		props.put("value.deserializer", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "value.deserializer"));

		KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<String, String>(
				props);
		kafkaConsumer.subscribe(Arrays.asList("twitter", "rss"));

		MongoDBConnector mongo = new MongoDBConnector(dbname);
		MongoDBConnector config = new MongoDBConnector(
				PropertyLoader.getPropertyValue(PropertyFile.database,
						"config.name"));

		LinkedList<String> keywords = new LinkedList<>();
		for (Document doc : config.getCollection("keywords").find()) {
			keywords.add(doc.getString("keyword"));
		}

		RedisConnector redis = new RedisConnector();

		Solr solr = new Solr();

		while (true) {
			ConsumerRecords<String, String> records = kafkaConsumer.poll(10);
			for (ConsumerRecord<String, String> record : records) {
				System.out.println("CONSUMER_ENHANCEDDATA: " + record.value());
				// decode JSON String
				JSONObject json = null;
				try {
					json = new JSONObject(record.value());
				} catch (JSONException e) {
					System.err
							.println("Not a valid JSON Object, continuing...");
					continue;
				}
				String id = json.getString("id");

				for (String keyword : keywords) {

					if (solr.search("\"" + json.getString("companyStripped")
							+ " " + keyword + "\"" + "~" + PROXIMITY, id)) {
						String text = json.getString("text");
						String link = json.getString("link");
						String date = json.getString("date");
						String company = json.getString("company");

						// Create JSON object to store in redis
						JSONObject redisJson = new JSONObject();
						redisJson.append("id", id);
						redisJson.append("text", text);
						redisJson.append("link", link);
						redisJson.append("date", date);
						redisJson.append("company", company);
						redisJson.append("keyword", keyword);

						// Create mongoDB document to store in mongoDB
						Document mongoDBdoc = new Document("text", text)
								.append("link", link).append("date", date)
								.append("company", company)
								.append("keyword", keyword);
						try {
							String title = json.getString("title");
							mongoDBdoc.append("title", title);
							redisJson.append("title", title);
						} catch (JSONException e) {
							// title field is optional and not saved if not
							// available
						}
						// Write to DB
						// Remove points for the collection name, because
						// they are not permitted in MongoDB
						mongo.writeToDb(
								mongoDBdoc,
								json.getString("companyStripped").replaceAll(
										"\\.", ""));
						redis.appendJSONToList(
								json.getString("companyStripped").replaceAll(
										"\\.", ""), redisJson);
					}
				}
				solr.delete(id);
			}
		}
	}
}