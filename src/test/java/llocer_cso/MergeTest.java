package llocer_cso;

import java.net.URI;

import com.llocer.ev.ocpi.modules.OcpiReceiver;
import com.llocer.ev.ocpi.msgs22.OcpiConnector;
import com.llocer.ev.ocpi.msgs22.OcpiConnector.PowerType;

public class MergeTest {
	public static void main( String[] args ) throws Exception {
		OcpiConnector c1 = new OcpiConnector();
		c1.setId( "foo" );
		c1.setPowerType( PowerType.AC_3_PHASE );
		System.out.println( c1 );
		
		OcpiConnector c2 = new OcpiConnector();
		c2.setTermsAndConditions( new URI("http://www.foo.com") );
		OcpiReceiver.mergeObjects( c1, c2 );
		System.out.println( c1 );
		
		c2 = new OcpiConnector();
		c1.setPowerType( PowerType.DC );
		OcpiReceiver.mergeObjects( c1, c2 );
		System.out.println( c1 );
		
	}
}
