package com.llocer.ev.cpo;

import java.util.Iterator;

public class Sugar {
	public static <T> T next( Iterator<T> it ) {
		return( it.hasNext() ? it.next() : null );
	}
}
