package org.myexample.hibcache;

import java.io.Serializable;
import java.util.Date;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
//@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // Specify caching strategy
public class Pet implements Serializable{


	@Id	
	@GeneratedValue (strategy = GenerationType.IDENTITY) 
	private int id;

	private String name;

	private String species;

	private char sex;

	private Date birth;

	// Getters and setters

	public Pet() {
	}

	public Pet(String name, String species, char sex, Date birth) {
		this.name = name;
		/*
		 * this.species = species; this.sex = sex; this.birth = birth;
		 */
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}


	public String getSpecies() { return species; }

	public void setSpecies(String species) { this.species = species; }

	public char getSex() { return sex; }

	public void setSex(char sex) { this.sex = sex; }

	public Date getBirth() { return birth; }

	public void setBirth(Date birth) { this.birth = birth; }


}
