package com.llocer.ev.server;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface OAMAgent {
	void executeOAM( String[] uri, HttpServletRequest request, HttpServletResponse response ) throws Exception;
}
