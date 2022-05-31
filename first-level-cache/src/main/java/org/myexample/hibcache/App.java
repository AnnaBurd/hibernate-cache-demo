package org.myexample.hibcache;

import java.util.List;

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

		// Get statistics of session factory (including second level cache statistics)
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
		
		
		printStatistics(sessionFactory.getStatistics());
		
	}

	public static void printStatistics(Statistics statistics) {
		System.out.println("Misses in second level cache:" + statistics.getSecondLevelCacheMissCount());
		System.out.println("Added to second level cache:" + statistics.getSecondLevelCachePutCount());
		System.out.println("Found in second level cache:" + statistics.getSecondLevelCacheHitCount());
		statistics.clear();
	}
}
