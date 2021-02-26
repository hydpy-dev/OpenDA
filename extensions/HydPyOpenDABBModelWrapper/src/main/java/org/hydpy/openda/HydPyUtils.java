/**
 * Copyright (c) 2019 by
 * - Bundesanstalt für Gewässerkunde
 * - Björnsen Beratende Ingenieure GmbH
 * All rights reserved.
 *
 * This file is Free Software under the under the terms of the
 * GNU Lesser General Public License (LGPL >=v3)
 * and comes with ABSOLUTELY NO WARRANTY! Check out the
 * documentation coming with HydPy for details.
 */
package org.hydpy.openda;

import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Instant;

/**
 * Some static utils.
 *
 * @author Gernot Belger
 */
public final class HydPyUtils
{
  // REMARK: copied from Observer OpenDA, API, which is sadly not public
  private static final double millisToDays = 1.0 / (1000 * 60 * 60 * 24);

  private static final double daysToMillis = (1000 * 60 * 60 * 24);

  private static final double mjdAtJanFirst1970 = 40587.0;

  private HydPyUtils( )
  {
    throw new UnsupportedOperationException();
  }

  public static String getOptionalSystemProperty( final String key, final String defaultValue )
  {
    final String value = System.getenv( key );
    if( StringUtils.isBlank( value ) )
      return defaultValue;

    return value;
  }

  public static String getRequiredProperty( final Properties properties, final String key )
  {
    final String value = properties.getProperty( key );
    if( StringUtils.isBlank( value ) )
      throw new RuntimeException( String.format( "Missing argument: %s", key ) );

    return value;
  }

  public static int getRequiredPropertyAsInt( final Properties properties, final String key )
  {
    final String value = getRequiredProperty( properties, key );
    return parseInt( key, value );
  }

  public static int getOptionalPropertyAsInt( final Properties properties, final String key, final int defaultValue )
  {
    final String value = properties.getProperty( key );
    if( StringUtils.isBlank( value ) )
      return defaultValue;

    return parseInt( key, value );
  }

  public static boolean getOptionalPropertyAsBoolean( final Properties properties, final String key, final boolean defaultValue )
  {
    final String value = properties.getProperty( key );
    if( StringUtils.isBlank( value ) )
      return defaultValue;

    return Boolean.parseBoolean( value );
  }

  private static int parseInt( final String key, final String value )
  {
    try
    {
      return Integer.parseInt( value );
    }
    catch( final NumberFormatException e )
    {
      final String mesage = String.format( "Value '%s' of environment variable '%s' must be an integer.", value, key );
      throw new RuntimeException( mesage, e );
    }
  }

  public static double[] parseDoubleArray( final String text )
  {
    final StringTokenizer st = new StringTokenizer( text, "[], " );

    final int countTokens = st.countTokens();

    final double[] values = new double[countTokens];

    for( int i = 0; i < values.length; i++ )
    {
      final String token = st.nextToken();
      values[i] = parseDouble( token );
    }

    return values;
  }

  private static double parseDouble( final String token )
  {
    if( "nan".equals( token ) )
      return Double.NaN;

    return Double.parseDouble( token );
  }

  public static String printDoubleArray( final double[] doubles )
  {
    final StringBuilder buffer = new StringBuilder( "[" );

    for( int i = 0; i < doubles.length; i++ )
    {
      buffer.append( doubles[i] );

      if( i < doubles.length - 1 )
        buffer.append( ',' );
    }

    buffer.append( ']' );
    return buffer.toString();
  }

  public static double dateToMjd( final Instant date )
  {
    return date.getMillis() * millisToDays + mjdAtJanFirst1970; // convert from millis to days and add offset for mjd
  }

  public static Instant mjdToDate( final double mjd )
  {
    final long timeInMillis = Math.round( (mjd - mjdAtJanFirst1970) * daysToMillis );
    return new Instant( timeInMillis );
  }
}