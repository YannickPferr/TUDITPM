package TUDITPM.Kafka;

import java.util.List;

import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class SearchTweetsTwitter4j {

	/*
	 * searches Twitter for posts associated with the keyword returns List with
	 * all Tweets found or null if nothing was found
	 */
	public List<Status> searchTweets(String keyword) {

		ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
		configurationBuilder.setDebugEnabled(true)
			.setOAuthConsumerKey(ApplicationCredentials.OAUTHCONSUMERKEY)
			.setOAuthConsumerSecret(ApplicationCredentials.OAUTHCONSUMERSECRET)
			.setOAuthAccessToken(ApplicationCredentials.OAUTHACCESSTOKEN)
			.setOAuthAccessTokenSecret(ApplicationCredentials.OAUTHACCESSTOKENSECRET);

		TwitterFactory twitterFactory = new TwitterFactory(configurationBuilder.build());
		Twitter twitter = twitterFactory.getInstance();

		Query query = new Query(keyword);
		// Maximum 100
		// query.setCount(100);
		QueryResult result = null;
		try {
			result = twitter.search(query);
		} catch (TwitterException e) {
			e.printStackTrace();
			System.err.println("Error, Couldnt fetch Twitter posts");
		}

		List<Status> tweets = null;
		if (result != null)
			tweets = result.getTweets();

		return tweets;
	}
}