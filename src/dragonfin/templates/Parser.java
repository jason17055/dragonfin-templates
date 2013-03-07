package dragonfin.templates;

import java.io.*;
import java.util.*;

class Parser
{
	TemplateToolkit toolkit;
	PushbackReader in;
	int lineno;
	int colno;
	int st;            //parser state (see nextToken)
	StringBuilder cur; //current token
	Token peekToken;
	boolean peeked;

	Parser(Reader in)
		throws IOException
	{
		this(null, new BufferedReader(in));
	}

	Parser(TemplateToolkit toolkit, BufferedReader in)
		throws IOException
	{
		this.toolkit = toolkit;
		this.in = new PushbackReader(in,1);
		this.st = 0;
		this.cur = new StringBuilder();
		this.lineno = 1;
		this.colno = 0;
	}

	static enum TokenType
	{
	//literals
		LITERAL_STRING,
		SINGLE_QUOTE_STRING,
		DOUBLE_QUOTE_STRING,
		NUMBER,
	//keywords
		DEFAULT,
		ELSE,
		ELSIF,
		END,
		FILTER,
		FOREACH,
		GET,
		IF,
		IN,
		INCLUDE,
		INSERT,
		SET,
		UNLESS,
		WRAPPER,
	//punctuation and operators
		COMMA,
		DOT,
		EQUAL,
		NOT_EQUAL,
		ASSIGN,
		OPEN_BRACKET,
		CLOSE_BRACKET,
		OPEN_PAREN,
		CLOSE_PAREN,
	//other
		IDENTIFIER,
		EOF;
	}

	static class Token
	{
		TokenType token;
		String text;

		Token(TokenType token, String text)
		{
			this.token = token;
			this.text = text;
		}
	}

	static final Token ASSIGN    = new Token(TokenType.ASSIGN, "=");
	static final Token DOT       = new Token(TokenType.DOT, ".");
	static final Token EQUAL     = new Token(TokenType.EQUAL, "==");
	static final Token FILTER    = new Token(TokenType.FILTER, "|");
	static final Token NOT_EQUAL = new Token(TokenType.NOT_EQUAL, "!=");
	static final Token OPEN_PAREN = new Token(TokenType.OPEN_PAREN, "(");
	static final Token CLOSE_PAREN= new Token(TokenType.CLOSE_PAREN, ")");
	static final Token OPEN_BRACKET = new Token(TokenType.OPEN_BRACKET, "[");
	static final Token CLOSE_BRACKET= new Token(TokenType.CLOSE_BRACKET, "]");
	static final Token COMMA = new Token(TokenType.COMMA, ",");

	private Token makeIdentifier(String s)
	{
		if (s.equals("DEFAULT"))
			return new Token(TokenType.DEFAULT, s);
		else if (s.equals("END"))
			return new Token(TokenType.END, s);
		else if (s.equals("ELSE"))
			return new Token(TokenType.ELSE, s);
		else if (s.equals("ELSIF"))
			return new Token(TokenType.ELSIF, s);
		else if (s.equals("FILTER"))
			return new Token(TokenType.FILTER, s);
		else if (s.equals("FOREACH"))
			return new Token(TokenType.FOREACH, s);
		else if (s.equals("GET"))
			return new Token(TokenType.GET, s);
		else if (s.equals("IF"))
			return new Token(TokenType.IF, s);
		else if (s.equals("IN"))
			return new Token(TokenType.IN, s);
		else if (s.equals("INCLUDE"))
			return new Token(TokenType.INCLUDE, s);
		else if (s.equals("INSERT"))
			return new Token(TokenType.INSERT, s);
		else if (s.equals("SET"))
			return new Token(TokenType.SET, s);
		else if (s.equals("UNLESS"))
			return new Token(TokenType.UNLESS, s);
		else if (s.equals("WRAPPER"))
			return new Token(TokenType.WRAPPER, s);
		else
			return new Token(TokenType.IDENTIFIER, s);
	}

	private Token makeSingleQuoteString(String s)
	{
		return new Token(TokenType.SINGLE_QUOTE_STRING, s);
	}

	private TokenType peekToken()
		throws IOException, TemplateSyntaxException
	{
		if (!peeked)
		{
			peeked = true;
			peekToken = nextToken_real();
		}
		return peekToken != null ? peekToken.token : TokenType.EOF;
	}

	private Token eatToken(TokenType expectedType)
		throws IOException, TemplateSyntaxException
	{
		peekToken();
		peeked = false;

		if (peekToken == null)
			throw unexpectedEof();

		if (peekToken.token != expectedType)
		{
			throw unexpectedToken(peekToken.token, expectedType);
		}
		return peekToken;
	}
			
	private Token nextToken_real()
		throws IOException, TemplateSyntaxException
	{
		if (st == -1) //end of file
			return null;

		while (st != -1)
		{
			int c = in.read();
			if (c == '\n')
			{
				lineno++;
				colno = 0;
			}
			else
			{
				colno++;
			}

			switch(st)
			{
			case 0:
				if (c == -1) {
					Token t = new Token(
						TokenType.LITERAL_STRING,
						cur.toString()
						);
					st = -1;
					return t;
				} else if (c == '[') {
					st = 1;
				} else {
					cur.append((char)c);
				}
				break;
			case 1:
				if (c == -1) {
					cur.append('[');
					Token t = new Token(
						TokenType.LITERAL_STRING,
						cur.toString()
						);
					st = -1;
					return t;
				} else if (c == '%') {
					Token t = new Token(
						TokenType.LITERAL_STRING,
						cur.toString()
						);
					cur = new StringBuilder();
					st = 2;
					return t;
				} else {
					cur.append('[');
					cur.append((char)c);
					st = 0;
				}
				break;
			case 2:
				if (c == '%') {
					st = 3;
				} else if (c == '.') {
					return DOT;
				} else if (c == '[') {
					return OPEN_BRACKET;
				} else if (c == ']') {
					return CLOSE_BRACKET;
				} else if (c == '(') {
					return OPEN_PAREN;
				} else if (c == ')') {
					return CLOSE_PAREN;
				} else if (c == ',') {
					return COMMA;
				} else if (c == '=') {
					st = 5;
				} else if (c == '|') {
					return FILTER;
				} else if (c == '\'') {
					st = 6;
				} else if (c == '"') {
					st = 9;
				} else if (Character.isJavaIdentifierStart(c)) {
					cur.append((char)c);
					st = 4;
				} else if (Character.isDigit(c)) {
					cur.append((char)c);
					st = 11;
				} else if (Character.isWhitespace(c)) {
					//do nothing
				} else if (c == '#') {
					st = 7;
				} else if (c == -1) {
					st = -1;
					return null;
				} else {
					throw unexpectedCharacter(c);
				}
				break;
			case 3: // token beginning with "%"
				if (c == -1) {
					throw unexpectedEof();
				} else if (c == ']') {
					assert cur.length() == 0;
					st = 0;
				} else {
					throw unexpectedCharacter(c);
				}
				break;
			case 4: // token beginning with [A-Za-z]
				if (Character.isJavaIdentifierPart(c))
				{
					// continues an identifier
					cur.append((char)c);
				}
				else
				{
					// end of identifier
					Token t = makeIdentifier(cur.toString());
					cur = new StringBuilder();
					st = 2;
					unread(c);
					return t;
				}
				break;

			case 5: // token beginning with "="
				if (c == '=')
				{
					st = 2;
					return EQUAL;
				}
				else
				{
					assert cur.length() == 0;
					st = 2;
					unread(c);
					return ASSIGN;
				}

			case 6: // single-quote-delimited string
				if (c == '\'')
				{
					// end of string
					Token t = makeSingleQuoteString(cur.toString());
					cur = new StringBuilder();
					st = 2;
					return t;
				}
				else if (c == -1)
				{
					throw unexpectedEof();
				}
				else
				{
					cur.append((char)c);
				}
				break;

			case 7: // token beginning with # (i.e. a comment)
				if (c == '%')
				{
					// this might end the comment
					st = 8;
				}
				else if (c == '\n')
				{
					// end of comment
					st = 2;
				}
				else if (c == -1)
				{
					st = 2;
					unread(c);
				}
				else
				{
					// ignore
				}
				break;

			case 8: // found a % inside a comment
				if (c == ']')
				{
					// end of comment, also: end of
					// directive
					assert cur.length() == 0;
					st = 0;
				}
				else
				{
					st = 7;
					unread(c);
				}
				break;

			case 9: // double-quote-delimited string
				if (c == '"')
				{
					// end of string
					Token t = new Token(
						TokenType.DOUBLE_QUOTE_STRING,
						cur.toString()
						);
					cur = new StringBuilder();
					st = 2;
					return t;
				}
				else if (c == -1)
				{
					throw unexpectedEof();
				}
				else if (c == '\\')
				{
					cur.append((char)c);
					st = 10;
				}
				else
				{
					cur.append((char)c);
				}
				break;

			case 10: // backslash within double-quoted string
				if (c == -1)
				{
					throw unexpectedEof();
				}
				else
				{
					cur.append((char)c);
					st=9;
				}
				break;

			case 11: //token beginning with [0-9]
				if (Character.isDigit(c))
				{
					cur.append((char)c);
				}
				else
				{
					// end of number
					Token t = new Token(
						TokenType.NUMBER,
						cur.toString());
					cur = new StringBuilder();
					st = 2;
					unread(c);
					return t;
				}
				break;

			default:
				throw new Error("Should be unreachable");
			}

			assert c != -1;
		}
		assert false;
		return null;
	}

	private void unread(int c)
		throws IOException
	{
		if (c == -1)
		{
			st = -1;
			return;
		}

		if (c == '\n')
		{
			//note- this does not correctly track the column
			//number, but presumably this is being called to set
			//the finite state machine back to a basic state,
			//which will read the '\n' again without error,
			//and the column number will again be correct.
			lineno--;
		}
		else
		{
			colno--;
		}
		in.unread(c);
	}

	private SyntaxException unexpectedCharacter(int c)
	{
		if (c == -1) return unexpectedEof();

		return new SyntaxException("Unexpected character ("+
			(c >= 33 && c < 127 ? ((char)c) : "\\"+c)
			+")");
	}

	private SyntaxException unexpectedEof()
	{
		return new SyntaxException("Unexpected EOF");
	}

	private SyntaxException unexpectedToken(TokenType actual)
	{
		return new SyntaxException("Found invalid "+actual);
	}

	private SyntaxException unexpectedToken(TokenType actual, TokenType expected)
	{
		return new SyntaxException("Found "+actual+" but expected "+expected);
	}

	class SyntaxException extends TemplateSyntaxException
	{
		SyntaxException(String message)
		{
			super(lineno, colno, message);
		}
	}

	Block parseIfBlock()
		throws IOException, TemplateSyntaxException
	{
		Block doc = new Block();

		for(;;)
		{
		switch (peekToken())
		{
		case LITERAL_STRING:
			doc.parts.add(eatToken(TokenType.LITERAL_STRING).text);
			break;
		case EOF:
			throw unexpectedEof();
		case END:
		case ELSE:
		case ELSIF:
			// leave the terminating token uneaten
			return doc;
		default:
			doc.parts.add(parseDirective());
			break;
		}
		}
	}

	Block parseBlock()
		throws IOException, TemplateSyntaxException
	{
		Block doc = new Block();

		for(;;)
		{
		switch (peekToken())
		{
		case LITERAL_STRING:
			doc.parts.add(eatToken(TokenType.LITERAL_STRING).text);
			break;
		case EOF:
			throw unexpectedEof();
		case END:
			eatToken(TokenType.END);
			return doc;
		default:
			doc.parts.add(parseDirective());
			break;
		}
		}
	}

	public Document parseDocument()
		throws IOException, TemplateSyntaxException
	{
		assert this.toolkit != null;

		Document doc = new Document(toolkit);
		TokenType token;
		while ( (token = peekToken()) != TokenType.EOF )
		{
			if (token == TokenType.LITERAL_STRING)
			{
				doc.parts.add(eatToken(token).text);
			}
			else
			{
				doc.parts.add(parseDirective());
			}
		}
		return doc;
	}

	//see "directive" in Template::Toolkit source
	private Directive parseDirective()
		throws IOException, TemplateSyntaxException
	{
		TokenType token = peekToken();
		if (token == TokenType.DEFAULT)
		{
			return parseDefaultDirective();
		}
		else if (token == TokenType.FILTER)
		{
			return parseFilterDirective();
		}
		else if (token == TokenType.FOREACH)
		{
			return parseForEachDirective();
		}
		else if (token == TokenType.GET)
		{
			return parseGetDirective();
		}
		else if (token == TokenType.IF
			|| token == TokenType.UNLESS)
		{
			return parseIfDirective(token);
		}
		else if (token == TokenType.INCLUDE)
		{
			return parseIncludeDirective();
		}
		else if (token == TokenType.INSERT)
		{
			return parseInsertDirective();
		}
		else if (token == TokenType.SET)
		{
			return parseSetDirective();
		}
		else if (token == TokenType.WRAPPER)
		{
			return parseWrapperDirective();
		}
		else if (isExpressionStart(token))
		{
			Expression expr = parseExpression();
			if (peekToken() == TokenType.ASSIGN)
			{
				eatToken(TokenType.ASSIGN);
				Expression rhs = parseExpression();
				return new SetDirective(expr, rhs);
			}
			Directive d = new GetDirective(expr);
			while (peekToken() == TokenType.FILTER)
			{
				d = parseChainedFilter(d);
			}
			if (peekToken() == TokenType.IF
				|| peekToken() == TokenType.UNLESS)
			{
				d = parseChainedCondition(d);
			}
			return d;
		}
		else
		{
			throw unexpectedToken(token);
		}
	}

	private DefaultDirective parseDefaultDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.DEFAULT);
		ArrayList<SetDirective> cmds = new ArrayList<SetDirective>();
		do
		{
			Expression lhs = parseExpression();
			eatToken(TokenType.ASSIGN);
			Expression rhs = parseExpression();
			cmds.add(new SetDirective(lhs, rhs));
		} while (isAssignmentStart(peekToken()));

		DefaultDirective d = new DefaultDirective(cmds);
		return d;
	}

	private IfDirective parseChainedCondition(Directive content)
		throws IOException, TemplateSyntaxException
	{
		TokenType t = peekToken();
		assert t == TokenType.IF || t == TokenType.UNLESS;
		eatToken(t);

		Expression condition = parseExpression();
		if (t == TokenType.UNLESS)
		{
			return new IfDirective(condition, Block.NULL, content);
		}
		else
		{
			return new IfDirective(condition, content, Block.NULL);
		}
	}

	private FilterDirective parseChainedFilter(Directive content)
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.FILTER);
		String filterName = parseItemName();
		return new FilterDirective(filterName, content);
	}

	private FilterDirective parseFilterDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.FILTER);
		String filterName = parseItemName();
		Block content = parseBlock();
		FilterDirective d = new FilterDirective(filterName, content);
		return d;
	}

	private ForEachDirective parseForEachDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.FOREACH);
		String varName = parseItemName();
		eatToken(TokenType.IN);
		Expression expr = parseExpression();
		Block content = parseBlock();
		return new ForEachDirective(
			varName, expr, content
			);
	}

	private GetDirective parseGetDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.GET);
		return new GetDirective(parseExpression());
	}

	private IncludeDirective parseIncludeDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.INCLUDE);
		Expression path = parseExpression();
		IncludeDirective d = new IncludeDirective(path);

		if (isSetListStart(peekToken()))
		{
			d.assignments = parseSetList();
		}
		return d;
	}

	List<SetDirective> parseSetList()
		throws IOException, TemplateSyntaxException
	{
		ArrayList<SetDirective> list = new ArrayList<SetDirective>();
		do
		{
			list.add(parseAssignment());
			if (peekToken() == TokenType.COMMA)
				eatToken(TokenType.COMMA);
		}
		while (isSetListStart(peekToken()));

		return list;
	}

	boolean isSetListStart(TokenType t)
	{
		return isAssignmentStart(t);
	}

	private InsertDirective parseInsertDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.INSERT);
		Expression path = parseExpression();
		return new InsertDirective(path);
	}

	private SetDirective parseSetDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.SET);
		return parseAssignment();
	}

	private IfDirective parseIfDirective(TokenType ifElse)
		throws IOException, TemplateSyntaxException
	{
		assert ifElse == TokenType.IF
			|| ifElse == TokenType.UNLESS
			|| ifElse == TokenType.ELSIF;

		eatToken(ifElse);
		Expression condition = parseExpression();
		if (ifElse == TokenType.UNLESS)
		{
			condition = new Expressions.NotExpression(condition);
		}

		Block ifBlock = parseIfBlock();

		if (peekToken() == TokenType.ELSE)
		{
			eatToken(TokenType.ELSE);
			Block elseBlock = parseBlock();
			return new IfDirective(condition, ifBlock, elseBlock);
		}
		else if (peekToken() == TokenType.ELSIF)
		{
			IfDirective elseIf = parseIfDirective(TokenType.ELSIF);
			return new IfDirective(condition, ifBlock, elseIf);
		}
		else if (peekToken() == TokenType.END)
		{
			eatToken(TokenType.END);
			return new IfDirective(condition, ifBlock, Block.NULL);
		}
		else
			throw unexpectedToken(peekToken());
	}

	private WrapperDirective parseWrapperDirective()
		throws IOException, TemplateSyntaxException
	{
		eatToken(TokenType.WRAPPER);
		Expression path = parseExpression();
		WrapperDirective d = new WrapperDirective(path);

		if (isSetListStart(peekToken()))
		{
			d.assignments = parseSetList();
		}

		d.content = parseBlock();
		return d;
	}

	private SetDirective parseAssignment()
		throws IOException, TemplateSyntaxException
	{
		Expression lhs = parseIdentifier();
		eatToken(TokenType.ASSIGN);
		Expression rhs = parseExpression();
		return new SetDirective(lhs, rhs);
	}

	boolean isAssignmentStart(TokenType t)
	{
		return isExpressionStart(t);
	}

	boolean isExpressionStart(TokenType t)
	{
		return t == TokenType.IDENTIFIER ||
			t == TokenType.SINGLE_QUOTE_STRING ||
			t == TokenType.DOUBLE_QUOTE_STRING ||
			t == TokenType.NUMBER;
	}

	private Expression parseArith()
		throws IOException, TemplateSyntaxException
	{
		return parseChain();
	}

	private Expression parseComparison()
		throws IOException, TemplateSyntaxException
	{
		Expression lhs = parseArith();
		TokenType t = peekToken();
		if (t == TokenType.EQUAL || t == TokenType.NOT_EQUAL)
		{
			eatToken(t);
			Expression rhs = parseArith();
			return new Expressions.CompareExpression(lhs, t, rhs);
		}
		return lhs;
	}

	public Expression parseExpression()
		throws IOException, TemplateSyntaxException
	{
		assert isExpressionStart(peekToken());
		Expression e = parseComparison();
		return e;
	}

	private Expression parseString(String rawString)
		throws IOException, TemplateSyntaxException
	{
		StringParser parser = new StringParser(rawString);
		return parser.parse();
	}

	private Expression parseArrayLiteral()
		throws IOException, TemplateSyntaxException
	{
		ArrayList<Expression> parts = new ArrayList<Expression>();
		eatToken(TokenType.OPEN_BRACKET);
		while (peekToken() != TokenType.CLOSE_BRACKET)
		{
			TokenType t = peekToken();
			if (t == TokenType.COMMA)
			{
				eatToken(t);
				continue;
			}
			Expression e = parseExpression();
			parts.add(e);
		}
		eatToken(TokenType.CLOSE_BRACKET);
		return new Expressions.ArrayLiteral(parts);
	}

	private Expression parseChain()
		throws IOException, TemplateSyntaxException
	{
		TokenType t = peekToken();
		if (t == TokenType.OPEN_PAREN)
		{
			throw new Error("TODO");
		}
		else if (t == TokenType.OPEN_BRACKET)
		{
			return parseArrayLiteral();
		}
		else if (t == TokenType.SINGLE_QUOTE_STRING)
		{
			return new Expressions.Literal(eatToken(t).text);
		}
		else if (t == TokenType.DOUBLE_QUOTE_STRING)
		{
			return parseString(eatToken(t).text);
		}
		else if (t == TokenType.NUMBER)
		{
			return parseNumber(eatToken(t).text);
		}

		return parseIdentifier();
	}

	private Expression parseNumber(String numberText)
		throws TemplateSyntaxException
	{
		try
		{
			int x = Integer.parseInt(numberText);
			return new Expressions.Literal(new Integer(x));
		}
		catch (NumberFormatException e)
		{
			throw new SyntaxException("Invalid number");
		}
	}

	private List<Expression> parseArgs()
		throws IOException, TemplateSyntaxException
	{
		ArrayList<Expression> args = new ArrayList<Expression>();
		for (;;)
		{
			if (peekToken() == TokenType.COMMA)
			{
				eatToken(TokenType.COMMA);
				continue;
			}
			else if (isExpressionStart(peekToken()))
			{
				Expression lhs = parseExpression();
				//TODO- support named arguments

				args.add(lhs);
				continue;
			}
			else
			{
				break;
			}
		}
		return args;
	}

	private Expression parseNode()
		throws IOException, TemplateSyntaxException
	{
		String itemName = parseItemName();
		if (peekToken() == TokenType.OPEN_PAREN)
		{
			eatToken(TokenType.OPEN_PAREN);
			List<Expression> args = parseArgs();
			eatToken(TokenType.CLOSE_PAREN);
			return new FunctionCall(itemName, args);
		}
		else
		{
			return new Variable(itemName);
		}
	}

	private Expression parseIdentifier()
		throws IOException, TemplateSyntaxException
	{
		Expression lhs = parseNode();
		while (peekToken() == TokenType.DOT)
		{
			eatToken(TokenType.DOT);
			Expression rhs = parseNode();
			if (rhs instanceof Variable)
			{
				String varName = ((Variable)rhs).variableName;
				lhs = new GetProperty(lhs, varName);
			}
			else if (rhs instanceof FunctionCall)
			{
				FunctionCall fc = (FunctionCall) rhs;
				String methodName = fc.functionName;
				List<Expression> args = fc.arguments;
				lhs = new MethodCall(lhs, methodName, args);
			}
			else
			{
				throw new Error("unexpected");
			}
		}
		return lhs;
	}

	private String parseItemName()
		throws IOException, TemplateSyntaxException
	{
		Token t = eatToken(TokenType.IDENTIFIER);
		return t.text;
	}

	static class FunctionCall extends Expression
	{
		String functionName;
		List<Expression> arguments;

		FunctionCall(String functionName, List<Expression> arguments)
		{
			this.functionName = functionName;
			this.arguments = arguments;
		}

		@Override
		Object evaluate(Context ctx)
			throws TemplateRuntimeException
		{
			throw new TemplateRuntimeException("not implemented");
		}
	}

	static class Variable extends Expression
	{
		String variableName;
		Variable(String variableName)
		{
			this.variableName = variableName;
		}

		@Override
		Object evaluate(Context ctx)
		{
			Object v = ctx.vars.get(variableName);
			return v;
		}
	}

	static class GetDirective implements Directive
	{
		Expression expr;
		GetDirective(Expression expr)
		{
			this.expr = expr;
		}

		public void execute(Context ctx)
			throws IOException, TemplateRuntimeException
		{
			Object v = expr.evaluate(ctx);
			if (v != null)
			{
				ctx.out.write(v.toString());
			}
		}
	}

	static class InsertDirective implements Directive
	{
		Expression pathExpr;
		InsertDirective(Expression pathExpr)
		{
			this.pathExpr = pathExpr;
		}

		public void execute(Context ctx)
			throws IOException, TemplateRuntimeException
		{
			String v = Value.asString(pathExpr.evaluate(ctx));
			InputStream is = ctx.toolkit.resourceLoader.getResourceStream(v);
			Reader r = new InputStreamReader(is);
			char [] buf = new char[4096];
			int nread;
			while ( (nread = r.read(buf, 0, 4096)) != -1 )
			{
				ctx.out.write(buf, 0, nread);
			}
			r.close();
		}
	}
}
