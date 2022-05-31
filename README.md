This article explains the basics of caching with Hibernate for default first-level cache and optional second-level cache, provides an extensive overview of general caching concepts, and includes example configurations of second-level cache for the Hibernate 6 with Ehcache 3 or Redis caching providers.

Table of contents:
1. [What is Caching?](#what-is-caching)
2. [Cache layers in Hibernate](#cache-layers-in-hibernate)
3. [First-level cache](#first-level-cache)
4. [Second-level cache](#second-level-cache)
    * [Second-level cache with Ehcache caching provider](#second-level-cache-with-ehcache-caching-provider)
    * [Second-level cache with Redis](#second-level-cache-with-redis)
        * [Hibernate configuration with Redisson](#hibernate-configuration-with-redisson)
        * [Redis as a primary database with Redisson](#redis-as-a-primary-database-with-redisson)
5. [Conclusion](#conclusion)

## What is Caching?

Cache is a high-speed data storage that stores a subset of popular data in a layer between the application and main data storage to increase average data serving speed.

For example, imagine a web store application that lives in the fast random-access memory of the server. The data about items in the store, prices, orders, etc., is stored in the database on the hard drive disk. When the user requests data, the application has to run requests to the database and fetch results before serving to the user, which slows down the whole process as secondary memory is times slower compared to the RAM.

<p align="center">
<img src="images_for_article/1-2.svg?raw=true" width="600">
</p>

Another observation is that not all data in the database is equally popular: as the 20-80 rule says, there are usually 20% top items that take 80% of requests, and the last 20% of requests are split over the not as popular 80% of the items.

Thus the idea is to store copies of the most popular items in the fast memory for fast serves, which improves response times and reduces the number of queries to the database.

<p align="center">
<img src="images_for_article/2.svg?raw=true" width="700">
</p>

> The simplest way to implement cache in Java applications is with the hashmap data structure like `java.util.HashMap<Key, Value>`, where Key is the primary key from the database and Value is the object from the database. 

## Cache layers in Hibernate

As an intermediary between application and database that is responsible for managing database queries, Hibernate ORM includes flexible support for caching: there are build-in caching tools as well as API for third-parties caching products.

<p align="center">
<img src="images_for_article/3.svg?raw=true" width="500">
</p>

**First level cache** - is automatically supported by the hibernate cache also called **persistence context** that is associated with the session object and thus can not be accessed after the session is closed.

**Second level cache** - is an **optional** cache bound to the SessionFactory life-cycle, thus allowing to share cache between session objects. By default second-level cache is turned off, to start the second-level caching the Hibernate needs to connect with the caching provider that supports API defined in JSR 107: JCACHE - Java Temporary Caching API Specification.

**Workflow of fetching entities**:
1. Hibernate first searches the entity in the first-level cache (JPA Persistence Context) of the current session. If the entity is found, the reference to this entity object is returned.
2. If the entity is not found in the first-level cache, Hibernate searches on the session factory level - in the second-level cache if there is one. If the entity is found, Hibernate adds it to the first-level cache and returns a reference to it.
3. If the entity is not found in both caches, Hibernate runs a query to the database and depending on cache settings adds the results of the query to the cache.


> Hibernate can cache **entities** and **collections of entities**. Aside from that, Hibernate can cache results of frequently executed **queries** with fixed parameter values, but this is disabled by default because of an overheard to keep track when query results become invalidated. Query caching is enabled with property `hibernate.cache.use_query_cache` in the configuration file.

## First-level cache

In Hibernate, once an entity becomes managed - after `persist()` method for new entities or after retrieving entity from the database with `find()`, `createQuery()` or similar methods. This entity is added to the internal cache of the current persistence context belonging to `Session` (or `EntityManager` in JPA persistence specification). The persistence context is also called the first-level cache, and it is enabled by default.

The example below shows how Hibernate manages first-level cache in a session and between different sessions. The important parameter to note is the number of JDBS connections made, as the main aim of the cache is to release the pressure on the database.

The entity is represented by a simple POJO class that has a corresponding table in a database with prefilled values:

```Java
@Entity
public class Pet {

	@Id	
	@GeneratedValue (strategy = GenerationType.IDENTITY) 
	private int id;
    
	private String name;
	
	private String species;
	
	private char sex;

	private Date birth;
    
    // Constructors, getters and setters
}
```
<p></p>

When retrieving an entity from the database, Hibernate stores it in the first level cache that is a hashmap data structure `Map<EntityUniqueKey, Object> entitiesByUniqueKey`. `Class EntityUniqueKey` defines four fields to store `String entityName`, `String uniqueKeyName`, `Object key`, and `Type keyType` that is an encapsulation of the entity name and its identifier. The `Object` is the stored entity itself.

The items in the first level cache are accessed by entity identifier, thus for entities stored in the cache Hibernate can skip requests by identifier to the database, as shown below for the `find()` method. Caching queries with parameters other than the primary key, for example, selecting entities by name, is not supported in the first-level cache: 

```Java
	// Open session - the first level cache is associated with this object
	Session session = sessionFactory.openSession();
	
	// Run first query - the cache is empty as session have just started, 
	// thus Hibernate loads entity from the database and adds it to the cache
	Pet pet1 = session.createQuery(
			"select p "
					+ "from Pet p "
					+ "where p.name = :name", Pet.class)
			.setParameter("name", "Fluffy")
			.getSingleResult();
	// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.name=?		
	System.out.println("Entity fetched: "+ pet1 + " " + pet1.getName());				
	// Entity fetched: org.myexample.hibcache.Pet@33a47707 Fluffy
	
	
	// Fetch the same entity from the database with find(Class<Pet> entityClass, Object primaryKey) method
	// If entity is found the first level cache the database request is skipped
	Pet pet2 = session.find(Pet.class, 1);
	// Hibernate: no query
	System.out.println("Entity fetched: "+ pet2 + " " + pet2.getName());		
	// Entity fetched: org.myexample.hibcache.Pet@33a47707 Fluffy
	
	
	// Repeat query with session.createQuery() method
	// Hibernate can't execute arbitrary SQL queries against the cache, and cache is only a subset 
	// of database data, so although the entity is already in cache the Hibernate has to 
	// query the database again
	Pet pet3 = session.createQuery(
			"select p "
					+ "from Pet p "
					+ "where p.name = :name", Pet.class)
			.setParameter("name", "Fluffy")
			.getSingleResult();
	// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.name=?
	System.out.println("Entity fetched: "+ pet3 + " " + pet3.getName());
	// Entity fetched: org.myexample.hibcache.Pet@33a47707 Fluffy
	// The fetched entity is the same instance of session persistence context as previous results
```
<p></p>

With transactional **write-behind strategy**, Hibernate does not immediately synchronize changes in entities from session persistence context with the database. Actual writing to the database goes after the `flush()` method is called. This allows batching multiple entities to improve writing performance. Depending on the current flush mode, the session automatically calls `flush()` when `Transaction.commit()` is called.

As shown in the example below, deleting entities from cache is done with `session.evict(Object object)` or synonymous `session.detatch(Object object)` methods. Evicting entity from persistence context before `flush()` will lose unflushed changes. 

```Java
	// The first fetch loads entity from the database and adds it to cache
	Pet pet1 = session.find(Pet.class, 1);
	// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.id=?
	System.out.println("Entity fetched: "+ pet1 + " " + pet1.getName());
	// Entity fetched: org.myexample.hibcache.Pet@87d9a01 Fluffy
	
	// Remove instance from the session cache
	session.evict(pet1);
	
	// As the entity was removed from cache, second fetch again loads it from the database
	Pet pet2 = session.find(Pet.class, 1);
	// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.id=?
	System.out.println("Entity fetched: "+ pet2 + " " + pet2.getName());
	// Entity fetched: org.myexample.hibcache.Pet@467045c4 Fluffy 
    // Note that the returned entity is a different object
```
<p></p>

The first level cache can be accessed only from the session it belongs to, after the session is closed the cache is discarded:

```Java
	// Add entity to the first level cache of session object:
	Session session = sessionFactory.openSession();		
	Pet pet1 = session.find(Pet.class, 1);
	// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.id=?
	System.out.println("Entity fetched: "+ pet1 + " " + pet1.getName());
	// Entity fetched: org.myexample.hibcache.Pet@87d9a01 Fluffy		
	
	// Try to access cache from another session instance:
	Session secondSession = sessionFactory.openSession();
	Pet pet2 = secondSession.find(Pet.class, 1);
	// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.id=?
	System.out.println("Entity fetched: "+ pet2 + " " + pet2.getName());
	// Entity fetched: org.myexample.hibcache.Pet@230a73f2 Fluffy
	// Hibernate repeats database query and fetches another object - 
	// the first level cache is not shared between sessions 
```
<p></p>

## Second-level cache

Second-level cache in Hibernate allows flexible cache management on the single SessionFactory-level or even a cluster cache on a class-by-class and collection-by-collection basis. This includes optional query caching in addition to entity and collections caching.

Different caching products can be integrated with Hibernate via JCache as regulated in the JSP107 API specification. Hibernate also supports direct integration with Echache 2.x or Infispan caching providers, but since Hibernate 6.0 it is preferable to use JCache as the standard API.

By default, only Hibernate entities marked as cacheable are stored in the second-level cache. This rule can be overridden with `jakarta.persistence.sharedCache.mode` in the configuration file with the following possible options:
* `ENABLE_SELECTIVE` (default and recommended) - entities marked as cacheable with `@Cachable` or `@Cache` annotations are cached in the second-level cache.
* `DISABLE_SELECTIVE` - all entities are cached except ones marked as non-cacheable with `@Cache(usage = CacheConcurrencyStrategy.NONE)` annotation
* `ALL` - entities are always cached
* `NONE` - entities are not cached

It should be noted that Hibernate does not check for changes in the database made by other applications or with direct database access, so there might be an inconsistency between database data and Hibernate caches. To reduce the probability of such errors, the cache entries at the second-level cache may be set to expire regularly by configuring the **Time To Live (TTL)** retention policy of the caching provider.

#### Second-level cache with Ehcache caching provider

Configuration of the integration between Hibernate and Ehcache greatly depends on the exact versions of both products. Hibernate provides a proprietary integration for the older Ehcache 2.x releases, integration of the newest versions of Hibernate with Ehcache 3.x requires JCache standardized API. The benefit of using the JCache is the potential to change the caching provider.

This article introduces an example of **Hibernate 6** and **Ehcache 3** configuration for the **Java 17** project.

The Maven dependencies for the project are defined in the pom.xml file as shown below. Note that changing product versions should be done with care, as, for example, Hibernate can require different configuration file settings and Echache can require different XML parsers.

```XML
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.myexample</groupId>
  <artifactId>hibcache</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  
  <properties>
  	    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>${maven.compiler.source}</maven.compiler.target>
        
        <hibernate.version>6.0.2.Final</hibernate.version>        
        <ehcache.version>3.10.0</ehcache.version>        
        <jaxb.api.version>2.3.1</jaxb.api.version>        
        <mysql.version>8.0.29</mysql.version>
  </properties>
  <dependencies>
  		<!-- Hibernate ORM -->
  		<dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-core</artifactId>
            <version>${hibernate.version}</version>
        </dependency>
        
        <!-- Integration for javax.cache (JSP107) into Hibernate -  
        standard API to connect second level cache providers  -->
        <dependency>
            <groupId>org.hibernate</groupId>
            <artifactId>hibernate-jcache</artifactId>
            <version>${hibernate.version}</version>
        </dependency>
        
        <!-- MySQL database connector - to connect Hibernate with 
        MySQL database via JDBC connector -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>${mysql.version}</version>
        </dependency>
        
        <!-- Echache - cache provider that supports JSP107 API specification -->
        <dependency>
            <groupId>org.ehcache</groupId>
            <artifactId>ehcache</artifactId>
            <version>${ehcache.version}</version>
        </dependency>
        
        <!-- JAXB Tools required by Echache to parse ehcache.xml configuration file -->
        <dependency>
            <groupId>javax.xml.bind</groupId>
            <artifactId>jaxb-api</artifactId>
            <version>${jaxb.api.version}</version>
        </dependency>
        <dependency>
            <groupId>com.sun.xml.bind</groupId>
            <artifactId>jaxb-impl</artifactId>
            <version>${jaxb.api.version}</version>
        </dependency>
  </dependencies>
  
  <build>
        <sourceDirectory>src/main/java</sourceDirectory>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
        </plugins>
    </build>  
</project>
```
<p></p>

Second step after configuring project dependencies is to tell Hibernate to turn on second level cache and what cache provider to connect to. This is done by setting properties in the Hibernate configuration file: 

```XML
<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE hibernate-configuration PUBLIC
        "-//Hibernate/Hibernate Configuration DTD 3.0//EN"
        "http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd">

<hibernate-configuration>

    <session-factory>
    
    	<!-- Database connection settings -->
        <property name="hibernate.connection.driver_class">com.mysql.cj.jdbc.Driver</property>
        <property name="hibernate.dialect">org.hibernate.dialect.MySQLDialect</property>
        <property name="hibernate.connection.url">jdbc:mysql://localhost/mydatabase</property>
        <property name="hibernate.connection.username">hibernate_user</property>
        <property name="hibernate.connection.password">123456</property>
        
        <property name="hibernate.connection.characterEncoding">utf8</property>
        <property name="hibernate.current_session_context_class">thread</property>
        
        <!-- Recreate database schema on Hibernate launch -->
        <!-- <property name="hibernate.hbm2ddl.auto">create-drop</property> -->
        
        <!-- Print Hibernate queries to terminal -->
        <property name="hibernate.show_sql">true</property>
        
        <!--  Second level cache settings -->
        <property name="hibernate.cache.region.factory_class">jcache</property>
        <property name="hibernate.javax.cache.provider">
            org.ehcache.jsr107.EhcacheCachingProvider
        </property>        
        <property name="hibernate.javax.cache.uri">ehcache.xml</property>
        <property name="hibernate.cache.use_second_level_cache">true</property>
        
        <!-- Generate statistics (includes second level cache hits/misses) -->
        <property name="hibernate.generate_statistics">true</property>
                
        <!--  List of XML mapping files and annotated classes -->
        <mapping class="org.myexample.hibcache.Pet"/>
        
    </session-factory>
</hibernate-configuration>
```
<p></p>

Integration between Hibernate and pluggable caching provider is defined with `hibernate.cache.region.factory_class` property, possible values are:
* `JCache` for integration with caching providers supporting JSP107 API (including Ehcache 3.x)
* `Ehcache` for integration with Ehcache versions 2.x
* `org.hibernate.cache.infinispan.JndiInfinispanRegionFactory` for Infispan 
* another implementation of the Hibernate `RegionFactory` Class

Caching provider to load is explicitly specified with `hibernate.javax.cache.provider` property. For the Ehcache 3 the value shoud be set to `org.ehcache.jsr107.EhcacheCachingProvider`. The name and location of the configuration file for the caching provider are defined with `hibernate.javax.cache.uri` property.

And `hibernate.cache.use_second_level_cache` is an optional propety to enable or disable second level caching. By default the second level caching is enabled if there is caching provider other than `NoCachingRegionFactory` (`hibernate.cache.region.factory_class` property is defined).

The exact caching settings are defined depending on the caching provider. With Ehcache 3 it is possible to define default and per entity storage limits, time to live, etc. Note that configuration templates vary, below is an example of `ehcache.xml` supplementing JCache (JSR-107) cache configurations.

```XML
<config
  xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
  xmlns='http://www.ehcache.org/v3'
  xsi:schemaLocation="
        http://www.ehcache.org/v3 http://www.ehcache.org/schema/ehcache-core-3.0.xsd">
  <!-- Cache settings for each entity class -->
  <cache alias="pet">
  	<!-- Fully qualified class name of key for the entity stored in cache Cache<K, V> -->
    <key-type>java.lang.Integer</key-type>
    <!-- Full class name of entity held in cache -->
    <value-type>org.myexample.hibcache.Pet</value-type>
    <!-- Optionally - remove old items from cache -->
    <expiry>
      <ttl unit="minutes">2</ttl>
    </expiry>
    <!-- Optionally - limit maximum number of entities in cache -->
    <heap unit="entries">10</heap>
  </cache>
</config>
```
<p></p>

Finally, add annotations to the entity class to indicate that it is cacheable:
* `@Cachable (jakarta.persistence.Cacheable)` is an optional JPA annotation to indicate whether or not the entity should be cached.
* `@Cache (org.hibernate.annotations.Cache)` is a Hibernate annotation to define caching strategy of a root entity or a collection. Has optional parameters: `usage` defines `CacheConcurrencyStrategy`, `region` defines where entities will be stored, and `include` defines whether or not lazy properties are included in the second-level cache.

Hibernate defines the following cache concurrency strategies:
* `READ_ONLY` - to cache reference data that is never updated
* `READ_WRITE` - caches data that is sometimes updated while maintaining the semantics of the "read committed" isolation level, uses soft locks on the cache.
* `NONSTRICT_READ_WRITE` - caches data that is sometimes updated without ever locking the cache, thus there are time windows after changes and before commits when stale data can be obtained from the cache.
* `TRANSACTIONAL` - a "synchronous" concurrency strategy that commits changes to both cache and database in the same transaction.

```Java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // Specify caching strategy
public class Pet {

	@Id	
	@GeneratedValue (strategy = GenerationType.IDENTITY) 
	private int id;
    
	private String name;
	
	private String species;
	
	private char sex;

	private Date birth;
	
	// Getters and setters
}
```
<p></p>

The application below demonstrates the work of the second-level cache. Second-level cache metrics can be enabled in the Hibernate configuration file with `hibernate.generate_statistics` property.

```Java
package org.myexample.hibcache;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.stat.Statistics;

public class App {

	public static void main(String[] args) {

		// Create session factory - second level cache belongs to instance of session factory
		SessionFactory sessionFactory;
		final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
				.configure("hibernate.cfg.xml")
				.build();
		try {
			sessionFactory = new MetadataSources( registry ).buildMetadata().buildSessionFactory();
		}		
		catch (Exception e) {			
			System.out.println("Could not create session factory: " + e.getMessage());
			StandardServiceRegistryBuilder.destroy( registry );
			return;
		}

		// Get statistics of session factory (including second-level cache statistics)
		Statistics statistics = sessionFactory.getStatistics();

		// First session - fetch entity from the database
		Session session = sessionFactory.openSession();
		Pet pet1 = session.find(Pet.class, 1);
		// Hibernate generates query to database and adds returned entity to level 1 and 2 caches:
		// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.id=?
		printStatistics(statistics);
		// Misses in second level cache:1   <---- not found in second level cache
		// Added to second level cache:1	<---- added to second level cache
		// Found in second level cache:0
		System.out.println("Entity fetched: "+ pet1 + " " + pet1.getName());
		// Entity fetched: org.myexample.hibcache.Pet@674aa626 Fluffy

		// Second session - fetch the same entity (without lvl2 cache will repeat database query)
		Session secondSession = sessionFactory.openSession();
		Pet pet2 = secondSession.find(Pet.class, 1);
		// Hibernate finds entity in second level cache and adds it to first level cache:
		// Hibernate: no query
		printStatistics(statistics);
		// Misses in second level cache:0
		// Added to second level cache:0
		// Found in second level cache:1	<---- found in second level cache
		System.out.println("Entity fetched: "+ pet2 + " " + pet2.getName());
		// Entity fetched: org.myexample.hibcache.Pet@298263fa Fluffy 
		// Note that although found in second level cache, the returned entity is stored 
		// in the first level cache (persistence context)

	}

	public static void printStatistics(Statistics statistics) {
		System.out.println("Misses in second level cache:" + statistics.getSecondLevelCacheMissCount());
		System.out.println("Added to second level cache:" + statistics.getSecondLevelCachePutCount());
		System.out.println("Found in second level cache:" + statistics.getSecondLevelCacheHitCount());
		statistics.clear();
	}
}
```

#### Second-level cache with Redis

**Redis (Remote Dictionary Server)** is a fast in-memory key-value database with integrated modules for search and modern data models like JSON, graph, time series, and artificial intelligence. It is often used as a look-aside or write-through cache, to store session information, for fast data ingest, and as a primary database. 

To use Redis as the second-level cache provider for Hibernate, the following steps are required:
* Install and launch Redis - it can be installed on the [desktop](#https://redis.io/docs/getting-started/installation/) in a few easy steps or accessed via Redis Cloud, note that installation takes some time.
* Integrate Hibernate with one of Redis clients via JCache API - Redis official documentation shares a [list of clients](#https://redis.io/docs/clients/#java) for all popular development languages. For Java, first in the list is the **Redisson** client that also supports JCache API, other popular clients are **Jedis** or **lettuce**.

###### Hibernate configuration with Redisson

This article presents an example configuration of **Hibernate 6** with **Redisson** cache provider in a **Java 17** project. Note that when it comes to integration, projects can be very version-sensitive.

The previous example shows integration with Ehcache via JCache API. Redisson also supports JCache API, thus there are only a few changes in configuration files:
add a dependency to Redisson instead of Ehcache and change factory_class and cache configuration file properties in Hibernate configuration file.

The dependency for Redisson is shown below. For Hibernate 6, the Redisson provides `redisson-hibernate-6` artifactID, and `redisson-hibernate-53` launches as well.

```XML
        <!-- Redisson - cache provider that supports JSP107 API specification -->	
		<dependency>
    		<groupId>org.redisson</groupId>
    		<artifactId>redisson-hibernate-6</artifactId>
    		<version>3.17.3</version>
		</dependency>
```
<p></p>

Setting second level cache provider in Hibernate configuration file:

```XML
        <!--  Second level cache settings -->
        <property name="hibernate.javax.cache.provider">
            org.ehcache.jsr107.EhcacheCachingProvider
        </property>
         <property name="hibernate.cache.region.factory_class">
         	org.redisson.hibernate.RedissonRegionFactory</property>
         <property name="hibernate.cache.redisson.config">redisson.yaml</property>       
        <property name="hibernate.cache.use_second_level_cache">true</property>
        <property name="hibernate.cache.use_query_cache">true</property>
```
<p></p>

> Open-source Redisson version limits performance Redis features like local cache, data partitioning or ultra-fast read/write. Extended `RegionFactory` classes are provided in paid versions of the product.

Redisson provides additional `hibernate.cache.redisson` properties to define cache settings per entity, collection, naturalid, query and timestamp regions inside the Hibernate configuration file. For example, limit maximum number of cached entities, set TTL and eviction policy:

```XML
<property name="hibernate.cache.redisson.entity.eviction.max_entries">100</property>
<property name="hibernate.cache.redisson.entity.expiration.time_to_live">100000</property>
<property name="hibernate.cache.redisson.entity.localcache.eviction_policy">LRU</property>
```
<p></p>

The `redisson.yaml` configuration file defines connection settings to Redis. In the example below, the Redis is installed on the local machine and running on default port 6379.

```YAML
singleServerConfig:
    address: "redis://127.0.0.1:6379"
```

> Redisson provides an extensive list of [configurations](#https://github.com/redisson/redisson/wiki/2.-Configuration) to Redis, from the number of threads and data codecs to load balancers.

With this minimal amount of changes, the transfer from Ehcache to Redisson caching provider is complete. The code below demonstrates the work of query caching with a second-level cache.

```Java
		// On first query Hibernate generates JDBC request
		List<Pet> pets = query.getResultList();		
		// Hibernate: select p1_0.id,p1_0.birth,p1_0.name,p1_0.sex,p1_0.species from Pet p1_0 where p1_0.species=?
		
		for(Pet pet: pets) {
			System.out.println(pet + " " + pet.getName()+ " " + pet.getSpecies());
		}
		// Output:
		// org.myexample.hibcache.Pet@13018f00 Fluffy dog
		// org.myexample.hibcache.Pet@1b6683c4 Yuna dog
		
		
		// Run second query with the same parameters and retrieve results from cache
		Query<Pet> secondQuery = session.createQuery(
				"select p "
						+ "from Pet p "
						+ "where p.species = :species", Pet.class)
				.setParameter("species", "dog")
				.setCacheable(true); // To look for results in cache query also need to be cacheable
		
		// On second cacheable query Hibernate retrieves data from cache without JDBC request
		pets = secondQuery.getResultList();
		
		for(Pet pet: pets) {
			System.out.println(pet + " " + pet.getName()+ " " + pet.getSpecies());
		}
		// Output:
		// org.myexample.hibcache.Pet@13018f00 Fluffy dog
		// org.myexample.hibcache.Pet@1b6683c4 Yuna dog
```

> Hibernate 6.0.1.Final version has a bug with query caching that throws a non-serializable exception, in the latest version 6.0.2.Final this problem is solved.

###### Redis as a primary database with Redisson

Originally, the main purpose of the databases was to store and manage effectively large volumes of data on the secondary memory. With cheaper prime memory, the new trend is to store data with in-memory databases like Redis. Indeed, there is a lot of complexity introduced by first mapping application objects to relational database storage, and then by adding cache layers with different synchronization strategies.

With one of the Redis clients like Redisson, it is possible to persist Java classes to the in-memory storage. The example below shows storing and fetching process for the Pet entity.

The requirement for the project is preinstalled and running Redis. For Maven users, Redisson client dependency should be added to the `pom.xml` file.

```XML
		<dependency>
    		<groupId>org.redisson</groupId>
    		<artifactId>redisson-hibernate-6</artifactId>
    		<version>3.17.3</version>
		</dependency>
```
<p></p>

The annotations `@REntiry`, `@RId`, and `@RIndex` specify the entity to be stored in the Redis database, its primary key, and searchable fields.

```Java
@REntity
public class Pet {

	@RId 
	private int id;
    
	@RIndex
	private String name;
	
	@RIndex
	private String species;
	
	@RIndex
	private char sex;

	@RIndex
	private Date birth;
}
```
<p></p>

The demo application shows how to set up the Redisson client and persist objects to Redis. As expected, setting up is much more straightforward compared with the Hibernate logic.

```Java
package org.myexample.hibcache;

import java.util.Collection;

import org.redisson.Redisson;
import org.redisson.api.RLiveObjectService;
import org.redisson.api.RedissonClient;
import org.redisson.api.condition.Conditions;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

public class App {

	public static void main(String[] args) {

		// Set up Redisson configuration properties
		SingleServerConfig config = new Config()
				.useSingleServer()
				.setAddress("redis://127.0.0.1:6379");

		// Launch Redisson cient and RLiveObjectService
		RedissonClient client = Redisson.create();
		RLiveObjectService liveObjectService = client.getLiveObjectService();

		// Save the object to the Redis
		Pet newPet = new Pet(10, "Carl", "pony",'M', null);
		liveObjectService.persist(newPet);

		// Query objects by parameters
		Collection<Pet> pets = liveObjectService.find(Pet.class, 
				Conditions.eq("species", "pony"));

		for (Pet pet: pets) {
			System.out.println(pet + " " + pet.getName()); 
			// Output: org.myexample.hibcache.Pet$ByteBuddy$9ehA3QEo@6d91790b Carl
		}
	}
}
```

#### Conclusion

This article provides an extensive overview of caching with Hibernate and shows example configurations for Hibernate and different caching providers. 

Overall, cache utilizes the speed advantage of volatile memory over traditional storage and thus helps to speed up data management. For Hibernate, cache is essential to reduce database workload and is supported both as Hibernate inner cache and API to work with caching providers. 

At the same time, cache should not be thought of as the ultimate solution to speed up the project - in some cases, a better design of the project may be the main source for performance optimization.
