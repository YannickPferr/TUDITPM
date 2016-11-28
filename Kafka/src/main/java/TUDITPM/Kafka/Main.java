package TUDITPM.Kafka;

import java.io.IOException;

import TUDITPM.Kafka.Loading.PropertyLoader;

/**
 * Main class to start all necessary consumers and producers. Each consumer and
 * producer should contain all necessary startup functions in its constructor.
 * 
 * @author Tobias Mahncke
 * @version 1.1
 */
public class Main {
	public static void main(String[] args) {
		try {
			new PropertyLoader();
		} catch (IOException e) {
			System.err.println("Could not load property files.");
			e.printStackTrace();
			System.exit(1);
		}
		new ConsumerTwitterStreamingAPI().start();
		new ProducerTwitterStreamingAPI().start();
	}
}