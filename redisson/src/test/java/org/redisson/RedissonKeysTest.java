package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.api.BatchResult;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;

public class RedissonKeysTest extends BaseTest {

    @Test
    public void testTouch() {
        redisson.getSet("test").add("1");
        redisson.getSet("test10").add("1");
        
        assertThat(redisson.getKeys().touch("test")).isEqualTo(1);
        assertThat(redisson.getKeys().touch("test", "test2")).isEqualTo(1);
        assertThat(redisson.getKeys().touch("test3", "test2")).isEqualTo(0);
        assertThat(redisson.getKeys().touch("test3", "test10", "test")).isEqualTo(2);
    }

    
    @Test
    public void testExists() {
        redisson.getSet("test").add("1");
        redisson.getSet("test10").add("1");
        
        assertThat(redisson.getKeys().countExists("test")).isEqualTo(1);
        assertThat(redisson.getKeys().countExists("test", "test2")).isEqualTo(1);
        assertThat(redisson.getKeys().countExists("test3", "test2")).isEqualTo(0);
        assertThat(redisson.getKeys().countExists("test3", "test10", "test")).isEqualTo(2);
    }
    
    @Test
    public void testType() {
        redisson.getSet("test").add("1");
        
        assertThat(redisson.getKeys().getType("test")).isEqualTo(RType.SET);
        assertThat(redisson.getKeys().getType("test1")).isNull();
    }
    
    @Test
    public void testEmptyKeys() {
        Iterable<String> keysIterator = redisson.getKeys().getKeysByPattern("test*", 10);
        assertThat(keysIterator.iterator().hasNext()).isFalse();
    }
    
    @Test
    public void testKeysByPattern() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();

        
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();
        
        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        int size = 10000;
        for (int i = 0; i < size; i++) {
            redisson.getBucket("test" + i).set(i);
        }
        
        assertThat(redisson.getKeys().count()).isEqualTo(size);
        
        Long noOfKeysDeleted = 0L;
            int chunkSize = 20;
            Iterable<String> keysIterator = redisson.getKeys().getKeysByPattern("test*", chunkSize);
            Set<String> keys = new HashSet<>();
            for (String key : keysIterator) {
                keys.add(key);

                if (keys.size() % chunkSize == 0) {
                    long res = redisson.getKeys().delete(keys.toArray(new String[keys.size()]));
                    assertThat(res).isEqualTo(chunkSize);
                    noOfKeysDeleted += res;
                    keys.clear();
                }
            }
            //Delete remaining keys
            if (!keys.isEmpty()) {
                noOfKeysDeleted += redisson.getKeys().delete(keys.toArray(new String[keys.size()]));
            }
        
        assertThat(noOfKeysDeleted).isEqualTo(size);
        
        redisson.shutdown();
        process.shutdown();
    }

    
    @Test
    public void testKeysIterablePattern() {
        redisson.getBucket("test1").set("someValue");
        redisson.getBucket("test2").set("someValue");

        redisson.getBucket("test12").set("someValue");

        Iterator<String> iterator = redisson.getKeys().getKeysByPattern("test?").iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            assertThat(key).isIn("test1", "test2");
        }
    }

    @Test
    public void testKeysIterable() throws InterruptedException {
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < 115; i++) {
            String key = "key" + Math.random();
            RBucket<String> bucket = redisson.getBucket(key);
            keys.add(key);
            bucket.set("someValue");
        }

        Iterator<String> iterator = redisson.getKeys().getKeys().iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            keys.remove(key);
            iterator.remove();
        }
        Assert.assertEquals(0, keys.size());
        Assert.assertFalse(redisson.getKeys().getKeys().iterator().hasNext());
    }

    @Test
    public void testRandomKey() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue1");

        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue2");

        assertThat(redisson.getKeys().randomKey()).isIn("test1", "test2");
        redisson.getKeys().delete("test1");
        Assert.assertEquals("test2", redisson.getKeys().randomKey());
        redisson.getKeys().flushdb();
        Assert.assertNull(redisson.getKeys().randomKey());
    }

    @Test
    public void testDeleteInCluster() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();

        
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();
        
        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        int size = 10000;
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add("test" + i);
            redisson.getBucket("test" + i).set(i);
        }
        
        long deletedSize = redisson.getKeys().delete(list.toArray(new String[list.size()]));
        
        assertThat(deletedSize).isEqualTo(size);
        
        redisson.shutdown();
        process.shutdown();
    }
    
    @Test
    public void testDeleteByPattern() {
        RBucket<String> bucket = redisson.getBucket("test0");
        bucket.set("someValue3");
        assertThat(bucket.isExists()).isTrue();

        RBucket<String> bucket2 = redisson.getBucket("test9");
        bucket2.set("someValue4");
        assertThat(bucket.isExists()).isTrue();

        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");
        assertThat(map.isExists()).isTrue();

        RMap<String, String> map2 = redisson.getMap("test3");
        map2.fastPut("1", "5");
        assertThat(map2.isExists()).isTrue();


        Assert.assertEquals(4, redisson.getKeys().deleteByPattern("test?"));
        Assert.assertEquals(0, redisson.getKeys().deleteByPattern("test?"));
    }

    @Test
    public void testDeleteByPatternBatch() {
        RBucket<String> bucket = redisson.getBucket("test0");
        bucket.set("someValue3");
        assertThat(bucket.isExists()).isTrue();

        RBucket<String> bucket2 = redisson.getBucket("test9");
        bucket2.set("someValue4");
        assertThat(bucket.isExists()).isTrue();

        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");
        assertThat(map.isExists()).isTrue();

        RMap<String, String> map2 = redisson.getMap("test3");
        map2.fastPut("1", "5");
        assertThat(map2.isExists()).isTrue();


        RBatch batch = redisson.createBatch();
        batch.getKeys().deleteByPatternAsync("test?");
        BatchResult<?> r = batch.execute();
        Assert.assertEquals(4L, r.getResponses().get(0));
    }
    
    
    @Test
    public void testFindKeys() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue");
        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");

        Collection<String> keys = redisson.getKeys().findKeysByPattern("test?");
        assertThat(keys).containsOnly("test1", "test2");

        Collection<String> keys2 = redisson.getKeys().findKeysByPattern("test");
        assertThat(keys2).isEmpty();
    }

    @Test
    public void testMassDelete() {
        RBucket<String> bucket0 = redisson.getBucket("test0");
        bucket0.set("someValue");
        RBucket<String> bucket1 = redisson.getBucket("test1");
        bucket1.set("someValue");
        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue");
        RBucket<String> bucket3 = redisson.getBucket("test3");
        bucket3.set("someValue");
        RBucket<String> bucket10 = redisson.getBucket("test10");
        bucket10.set("someValue");

        RBucket<String> bucket12 = redisson.getBucket("test12");
        bucket12.set("someValue");
        RMap<String, String> map = redisson.getMap("map2");
        map.fastPut("1", "2");

        Assert.assertEquals(7, redisson.getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
        Assert.assertEquals(0, redisson.getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
    }

    @Test
    public void testCount() {
        Long s = redisson.getKeys().count();
        assertThat(s).isEqualTo(0);
        redisson.getBucket("test1").set(23);
        s = redisson.getKeys().count();
        assertThat(s).isEqualTo(1);
    }

}
