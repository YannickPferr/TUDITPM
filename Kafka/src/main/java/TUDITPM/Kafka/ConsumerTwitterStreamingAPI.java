package TUDITPM.Kafka;

import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bson.Document;
import org.json.JSONException;
import org.json.JSONObject;

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
public class ConsumerTwitterStreamingAPI {

	public ConsumerTwitterStreamingAPI() {
		Properties props = new Properties();
		props.put("bootstrap.servers", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "bootstrap.servers"));
		props.put("group.id", "group-1");
		props.put("enable.auto.commit", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "enable.auto.commit"));
		props.put("auto.commit.interval.ms", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "enable.auto.commit"));
		props.put("auto.offset.reset", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "auto.offset.reset"));
		props.put("session.timeout.ms", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "session.timeout.ms"));
		props.put("key.deserializer", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "key.deserializer"));
		props.put("value.deserializer", PropertyLoader.getPropertyValue(
				PropertyFile.kafka, "value.deserializer"));

		KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(props);
		kafkaConsumer.subscribe(Arrays.asList("twitter"));

		MongoDBWriter mongo = new MongoDBWriter("dbtest", "testcollection");

		while (true) {
			ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
			int missedTweets = 0;
			for (ConsumerRecord<String, String> record : records) {
				System.out.println(record.value());
				try {
					// decode JSON String
					JSONObject jObj = new JSONObject(record.value());
					String text = jObj.getString("text");
					String timeNdate = jObj.getString("created_at");

					JSONObject user = jObj.getJSONObject("user");
					String username = user.getString("screen_name");
					String location = (!user.get("location").toString()
							.equals("null")) ? user.getString("location") : "";

					// Write to DB
					mongo.writeToDb(new Document("username", username)
							.append("location", location)
							.append("timeNDate", timeNdate)
							.append("text", text));
				} catch (JSONException e) {
					missedTweets++;
				}
			}
			if (missedTweets > 0) {
				System.out.println(missedTweets + " Tweets missed");
			}
		}
	}
}