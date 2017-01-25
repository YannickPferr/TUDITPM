package TUDITPM.Kafka.DBConnectors;

import org.json.JSONObject;

import TUDITPM.Kafka.Loading.PropertyFile;
import TUDITPM.Kafka.Loading.PropertyLoader;
import redis.clients.jedis.Jedis;

public class RedisConnector {
	private Jedis jedis;
	
	public RedisConnector(){
		jedis = new Jedis(PropertyLoader.getPropertyValue(PropertyFile.database, "REDIS_HOST"));
		jedis.ping();
	}
	
	public void appendJSONToList(String key, JSONObject value){
		jedis.lpush(key, value.toString());
	}
}
