package dragonfin.templates;

abstract class Argument
{
	Expression expr;
}

class SimpleArgument extends Argument
{
	SimpleArgument(Expression expr)
	{
		this.expr = expr;
	}
}

class NamedArgument extends Argument
{
	Expression ident;
	NamedArgument(Expression ident, Expression expr)
	{
		this.ident = ident;
		this.expr = expr;
	}
}
