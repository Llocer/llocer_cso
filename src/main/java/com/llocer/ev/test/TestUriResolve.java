package com.llocer.ev.test;

import java.net.URI;
import java.net.URISyntaxException;

public class TestUriResolve {
	public static void main(String[] args) throws URISyntaxException {
        URI uri = new URI( "http://www.example.org");
        System.out.println( "*** "+uri+" ***" );
        System.out.println( uri.resolve( "bar") );
        System.out.println( uri.resolve( "/bar") );
        System.out.println( uri.resolve( "bar/") );
        System.out.println( uri.resolve( "/bar/") );
        System.out.println();
        
        uri = new URI( "http://www.example.org/");
        System.out.println( "*** "+uri+" ***" );
        System.out.println( uri.resolve( "bar") );
        System.out.println( uri.resolve( "/bar") );
        System.out.println( uri.resolve( "bar/") );
        System.out.println( uri.resolve( "/bar/") );
        System.out.println();

        uri = new URI( "http://www.example.org/foo1/foo2");
        System.out.println( "*** "+uri+" ***" );
        System.out.println( uri.resolve( "bar") );
        System.out.println( uri.resolve( "/bar") );
        System.out.println( uri.resolve( "bar/") );
        System.out.println( uri.resolve( "/bar/") );
        System.out.println();
        
        uri = new URI( "http://www.example.org/foo1/foo2/");
        System.out.println( "*** "+uri+" ***" );
        System.out.println( uri.resolve( "bar") );
        System.out.println( uri.resolve( "/bar") );
        System.out.println( uri.resolve( "bar/") );
        System.out.println( uri.resolve( "/bar/") );
    }
}
