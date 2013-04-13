import java.io.*;
import java.util.*;
import dragonfin.templates.*;
import javax.script.Bindings;
import javax.script.SimpleBindings;

public class TestArgs
{
	static class MyFunction implements Function
	{
		public Object invoke(Bindings args)
		{
			System.out.print("  ARGS: [ ");
			int count = ((Number) args.get("#")).intValue();
			for (int i = 0; i < count; i++) {
				if (i != 0 ) { System.out.print(", "); }
				System.out.print(args.get(new Integer(i+1).toString()));
			}
			System.out.println(" ]");
			System.out.print("  NAMED: { ");
			int i = 0;
			for (String k : args.keySet()) {
				if ("#0123456789".indexOf(k.charAt(0)) != -1)
					continue;
				if (0 != i++) {
					System.out.print(", ");
				}
				System.out.print(k + " => " + args.get(k));
			}
			System.out.println(" }");
			return "";
		}
	}
	static MyFunction ff = new MyFunction();

	public static void main(String [] args)
		throws Exception
	{
		TemplateToolkit tt = new TemplateToolkit(
			new DefaultResourceLoader()
			);
		SimpleBindings env = new SimpleBindings();
		env.put("args", ff);
		tt.process("args.tt", env, new PrintWriter(System.out));
	}
}
