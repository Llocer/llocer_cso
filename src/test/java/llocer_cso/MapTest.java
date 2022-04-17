package llocer_cso;

import java.util.HashMap;
import java.util.Map;

interface I1 {
	public int foo();
}

class C1 implements I1 {

	@Override
	public int foo() {
		return 0;
	}
	
}

public class MapTest {
	public static void main( String[] args ) throws Exception {
		Map<I1,Integer> map = new HashMap<I1,Integer>();
		I1 i1 = new C1();
		map.put( i1, 14 );
	}
}
