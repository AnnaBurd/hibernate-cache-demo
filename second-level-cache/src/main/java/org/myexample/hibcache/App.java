package org.myexample.hibcache;

import java.util.List;

import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.Query;
import org.hibernate.stat.Statistics;

public class App {

	public static void main(String[] args) {

		// Create session factory - query cache belongs to second-level cache of the sessionFactory
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

		Session session = sessionFactory.openSession();
		
		// Run query and cache results		
		Query<Pet> query = session.createQuery(
				"select p "
						+ "from Pet p "
						+ "where p.species = :species", Pet.class)
				.setParameter("species", "dog")
				.setCacheable(true); // To store query results in cache query need to be set cacheable
		
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
		
		sessionFactory.getCache().evictAll();

		sessionFactory.close();

	}

	public static void printStatistics(Statistics statistics) {
		System.out.println("Misses in second level cache:" + statistics.getSecondLevelCacheMissCount());
		System.out.println("Added to second level cache:" + statistics.getSecondLevelCachePutCount());
		System.out.println("Found in second level cache:" + statistics.getSecondLevelCacheHitCount());
		statistics.clear();
	}
}
