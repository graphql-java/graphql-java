import java.util.*;
class ageexception extends Exception
{

	ageexception(String s)
	{
		super(s);
	}
}
class testing
{
	public static void validate(int age) throws Exception
	{
		if(age<18)
		{
			throw new ageexception("not allowed to vote");
		}
		else{
			System.out.println("plz vote for me");
		}
	}
}
class exp14semil extends testing
{
	public static void main(String args[])
	{
		 int ch;
		Scanner sc = new Scanner(System.in);
		System.out.println("enter the age");
		ch=sc.nextInt();
		try
		{
			validate(ch);
		}
		catch(Exception e)
		{
			System.out.println("exception occured = "+e);
		}
	}
}