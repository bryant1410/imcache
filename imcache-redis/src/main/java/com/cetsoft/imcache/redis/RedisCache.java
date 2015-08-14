package com.cetsoft.imcache.redis;

import java.util.concurrent.atomic.AtomicLong;

import com.cetsoft.imcache.cache.AbstractCache;
import com.cetsoft.imcache.cache.CacheLoader;
import com.cetsoft.imcache.cache.EvictionListener;
import com.cetsoft.imcache.cache.search.IndexHandler;
import com.cetsoft.imcache.redis.client.Client;
import com.cetsoft.imcache.serialization.Serializer;

/**
 * The Class RedisCache is a cache that uses redis server.
 * to store or retrieve data by serializing items into bytes. To do so,
 * RedisCache uses a redis client to talk to redis server. Any operation within
 * this cache is a command to redis.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class RedisCache<K, V> extends AbstractCache<K, V> {
	
	/** The client. */
	Client client;
	
	/** The serializer. */
	Serializer<Object> serializer;
	
	/** The hit. */
	protected AtomicLong hit = new AtomicLong();

	/** The miss. */
	protected AtomicLong miss = new AtomicLong();
	
	/**
	 * Instantiates a new redis cache.
	 *
	 * @param cacheLoader the cache loader
	 * @param evictionListener the eviction listener
	 * @param indexHandler the index handler
	 * @param serializer the serializer
	 * @param client the client
	 */
	public RedisCache(CacheLoader<K, V> cacheLoader, EvictionListener<K, V> evictionListener,
			IndexHandler<K, V> indexHandler, Serializer<Object> serializer, Client client) {
		super(cacheLoader, evictionListener, indexHandler);
		this.client = client;
		this.serializer = serializer;
	}

	/* (non-Javadoc)
	 * @see com.cetsoft.imcache.cache.Cache#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public void put(K key, V value) {
		try {
			client.set(serializer.serialize(key), serializer.serialize(value));
		} catch (Exception e) {
			throw new RedisCacheException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.cetsoft.imcache.cache.Cache#get(java.lang.Object)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public V get(K key) {
		try {
			byte[] serializedKey = serializer.serialize(key);
			V value = (V) serializer.deserialize(client.get(serializedKey));
			if(value == null){
				miss.incrementAndGet();
				value = cacheLoader.load(key);
				byte[] serializedValue = serializer.serialize(value);
				client.set(serializedKey, serializedValue);
			}
			else{
				hit.incrementAndGet();
			}
			return value;
		} catch (Exception e) {
			throw new RedisCacheException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.cetsoft.imcache.cache.Cache#invalidate(java.lang.Object)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public V invalidate(K key) {
		try {
			byte[] serializedKey = serializer.serialize(key);
			byte[] serializedValue = client.expire(serializedKey);
			V value = (V) serializer.deserialize(serializedValue);
			evictionListener.onEviction(key, value);
			return value;
		} catch (Exception e) {
			throw new RedisCacheException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.cetsoft.imcache.cache.Cache#contains(java.lang.Object)
	 */
	@Override
	public boolean contains(K key) {
		return get(key) != null;
	}

	/* (non-Javadoc)
	 * @see com.cetsoft.imcache.cache.Cache#clear()
	 */
	@Override
	public void clear() {
		try {
			client.flushdb();
		} catch (Exception e) {
			throw new RedisCacheException(e);
		}
	}

	/* (non-Javadoc)
	 * This method returns hit ratio per cache. It doesn't have ability to aggregate
	 * data from different JVM and caches.
	 * @see com.cetsoft.imcache.cache.Cache#hitRatio()
	 */
	@Override
	public double hitRatio() {
		return hit.get() / (hit.get() +  miss.get());
	}

	/* (non-Javadoc)
	 * @see com.cetsoft.imcache.cache.Cache#size()
	 */
	@Override
	public int size() {
		try {
			return client.dbsize();
		} catch (Exception e) {
			throw new RedisCacheException(e);
		}
	}

}