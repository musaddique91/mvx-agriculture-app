package com.mvx.agriculture.data;

/** One commodity's rates at one market on one day, in rupees per quintal. */
public class MandiPrice {
    public String commodity;
    public String variety;
    public String market;
    public String district;
    public String date;
    public int minPrice;
    public int maxPrice;
    public int modalPrice;
}
