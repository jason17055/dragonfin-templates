package dragonfin.templates;

import java.util.*;
import javax.script.SimpleBindings;

class FunctionCall extends Expression
{
	String functionName;
	List<Argument> arguments;

	FunctionCall(String functionName, List<Argument> arguments)
	{
		this.functionName = functionName;
		this.arguments = arguments;
	}

	@Override
	Object evaluate(Context ctx)
		throws TemplateRuntimeException
	{
		SimpleBindings argValues = new SimpleBindings();
		for (int i = 0; i < arguments.size(); i++) {
			Object v = arguments.get(i).expr.evaluate(ctx);
			argValues.put(new Integer(i+1).toString(), v);
		}

		if (ctx.toolkit.filters.containsKey(functionName)) {
			// apply a filter
			Filter f = ctx.toolkit.filters.get(functionName);
			return f.apply(Value.asString(argValues.get("1")));
		}
		else {
			throw new TemplateRuntimeException("not implemented ("+functionName+")");
		}
	}
}
