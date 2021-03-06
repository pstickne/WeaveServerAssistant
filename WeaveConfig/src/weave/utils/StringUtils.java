package weave.utils;

import weave.Globals;

public class StringUtils extends Globals
{
	/**
	 * Check to see if the <code>test</code> word is at the 
	 * beginning of the <code>word</code>.
	 * <p>
	 * Examples:
	 * <pre>
	 * StringUtils.beginsWith("Elephant", "Ele") 	returns true
	 * StringUtils.beginsWith("Racecar", "Car") 	returns false
	 * </pre>
	 * </p>
	 * 
	 * @param word The base word you want to test
	 * @param test The word that you are testing with
	 * @return <code>true</code> if the test is at the beginning, false otherwise
	 * 
	 * @see #endsWith(String, String)
	 */
	public static boolean beginsWith(String word, String test)
	{
		return word.substring(0, test.length()).equals(test);
	}
	
	/**
	 * Check to see if the <code>test</code> word is at the
	 * end of the <code>word</code>.
	 * <p>
	 * Examples:
	 * <pre>
	 * StringUtils.endsWith("Racecar", "car")	returns true
	 * StringUtils.endWith("Computer", "com")	returns false
	 * </pre>
	 * </p>
	 * 
	 * @param word The base word you want to test
	 * @param test The word that you are testing with
	 * @return <code>true</code> if the test is at the end, false otherwise
	 * 
	 * @see #beginsWith(String, String)
	 */
	public static boolean endsWith(String word, String test)
	{
		return word.substring(word.length() - test.length(), word.length()).equals(test);
	}
	
	/**
	 * Repeat a String <code>repeat</code> times to form a new String.
	 * <p>
	 * Examples:
	 * <pre>
	 * StringUtils.repeat(null, 2)  = null
	 * StringUtils.repeat("", 2)    = ""
	 * StringUtils.repeat("a", 3)   = "aaa"
	 * </pre>
	 * </p>
	 * 
	 * @param str The String to repeat, may be null
	 * @param repeat The number of times to repeat <code>str</code>, negative treated as zero
	 * @return A new String consisting of the original String repeated, <code>null</code> if null String input
	 */
	public static String repeat(String str, int repeat)
	{
		String s = "";
		if( str == null ) return null;
		if( repeat < 0 ) return "";
		for( int i = 0; i < repeat; ++i ) s += str;
		
		return s;
	}
	
	/**
	 * Truncates the <code>str</code> to the desired <code>length</code> 
	 * if the length is longer then the length of <code>str</code>. Otherwise,
	 * it will just return <code>str</code>. 
	 * 
	 * @param str The string to truncate
	 * @param length The length to truncate the string to
	 * @return The string tuncated to the desired length
	 */
	public static String truncate(String str, int length)
	{
		if( str.length() < length )
			return str;
		
		return str.substring(0, length);
	}
	
	/**
	 * Left pad the desired <code>text</code> with the <code>padding</code> value to fill the <code>width</code>.
	 * 
	 * @param text The text to display
	 * @param padding The padding value that will get repeated
	 * @param width The width of the text + padding lengths
	 * @return The padded string
	 * 
	 * @see #rpad(String, String, int)
	 */
	public static String lpad(String text, String padding, int width)
	{
		return repeat(padding, (width - text.length()) / padding.length()) + text;
	}
	
	/**
	 * Right pad the desired <code>text</code> with the <code>padding</code> value to fill the <code>width</code>.
	 * 
	 * @param text The text to display
	 * @param padding The padding vvalue that will get repeated
	 * @param width The width of the text + padding lengths
	 * @return The padded string
	 * 
	 * @see #lpad(String, String, int)
	 */
	public static String rpad(String text, String padding, int width)
	{
		return text + repeat(padding, (width - text.length()) / padding.length());
	}
}
