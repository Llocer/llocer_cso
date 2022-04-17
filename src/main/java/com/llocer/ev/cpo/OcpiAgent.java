package com.llocer.ev.cpo;

import com.llocer.ev.ocpi.server.OcpiAgentId;
import com.llocer.ev.ocpi.server.OcpiRequestData;
import com.llocer.ev.ocpi.server.OcpiResult;

public interface OcpiAgent {
	public OcpiAgentId getId();
	public OcpiResult<?> executeRequest( OcpiRequestData oreq ) throws Exception;
}
