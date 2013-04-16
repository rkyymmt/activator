package models;

public class StockQuote {
    String id;
    String t; // ticker
    String e; // exchange
    
    // latest when open
    Double l;
    Double l_cur;
    Double c;
    Double cp;
    
    // after closing
    Double el;
    Double el_cur;
    Double ec;
    Double ecp;
}
