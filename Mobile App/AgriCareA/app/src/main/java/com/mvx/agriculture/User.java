package com.mvx.agriculture;


public class User {
    private  int id;
    private String email;
    private String pwd;

    public int getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPwd() {
        return pwd;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getBirth() {
        return birth;
    }

    /** "City, Region" when a region is known, otherwise just the city. */
    public String getLocation() {
        if (region == null || region.isEmpty()) {
            return city == null ? "" : city;
        }
        return city + ", " + region;
    }

    public User(String email, String pwd, String city, String birth) {
        this(email, pwd, city, birth, null);
    }

    public User(String email, String pwd, String city, String birth, String region) {
        this.email = email;
        this.pwd = pwd;
        this.city = city;
        this.birth = birth;
        this.region = region;
    }

    private String city;

    private String region;

    public User(int id, String email, String pwd, String city, String birth) {
        this(id, email, pwd, city, birth, null);
    }

    public User(int id, String email, String pwd, String city, String birth, String region) {
        this.id = id;
        this.email = email;
        this.pwd = pwd;
        this.city = city;
        this.birth = birth;
        this.region = region;
    }

    private String birth;



}
