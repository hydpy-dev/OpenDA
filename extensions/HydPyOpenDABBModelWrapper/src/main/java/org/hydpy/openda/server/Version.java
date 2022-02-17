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
package org.hydpy.openda.server;

import org.apache.commons.lang3.text.StrMatcher;
import org.apache.commons.lang3.text.StrTokenizer;

/**
 * Represents a version either from HydPy or OpenDA.
 * <br/>
 * HydPyVersion: major.minor[.|a]bugfix
 * <br/>
 * OpenDaVersion: major.minor.bugfix.qualifier
 *
 * @author Gernot Belger
 */
final class Version implements Comparable<Version>
{
  private final int m_major;

  private final int m_minor;

  private final int m_bugfix;

  private final String m_qualifier;

  private final boolean m_isAlpha;

  public static Version parse( final String versionText ) throws HydPyServerException
  {
    final StrMatcher charSetMatcher = StrMatcher.charSetMatcher( '.', 'a' );
    final StrTokenizer tokenizer = new StrTokenizer( versionText, charSetMatcher );
    tokenizer.setEmptyTokenAsNull( true );

    final String[] tokens = new String[3];
    for( int i = 0; i < 3; i++ )
    {
      if( tokenizer.hasNext() )
        tokens[i] = tokenizer.next();
      else
        throw new HydPyServerException( String.format( "Received invalid version string: %s", versionText ) );
    }

    final int alphaPos = tokens[0].length() + tokens[1].length() + 1;
    final boolean isAlpha = versionText.charAt( alphaPos ) == 'a';

    final int qualifierPos = alphaPos + tokens[2].length() + 2;
    final String qualifier;
    if( versionText.length() >= qualifierPos )
      qualifier = versionText.substring( qualifierPos );
    else
      qualifier = null;

    final int major = Integer.parseInt( tokens[0] );
    final int minor = Integer.parseInt( tokens[1] );
    final int bugfix = Integer.parseInt( tokens[2] );

    return new Version( major, minor, bugfix, qualifier, isAlpha );
  }

  public Version( final int major, final int minor, final int bugfix, final String qualifier, final boolean isAlpha )
  {
    m_major = major;
    m_minor = minor;
    m_bugfix = bugfix;
    m_qualifier = qualifier;
    m_isAlpha = isAlpha;
  }

  @Override
  public String toString( )
  {
    final StringBuilder buffer = new StringBuilder();
    buffer.append( m_major );
    buffer.append( '.' );
    buffer.append( m_minor );
    if( m_isAlpha )
      buffer.append( 'a' );
    else
      buffer.append( '.' );
    buffer.append( m_bugfix );

    if( m_qualifier != null )
      buffer.append( '.' ).append( m_qualifier );

    return buffer.toString();
  }

  @Override
  public int compareTo( final Version o )
  {
    final int majorComp = m_major - o.m_major;
    if( majorComp != 0 )
      return majorComp;

    final int minorComp = m_minor - o.m_minor;
    if( minorComp != 0 )
      return minorComp;

    /* make alpha version numbers very small, so they are always less than non-alpha versions */
    final int mBugfix = m_isAlpha ? Integer.MIN_VALUE + m_bugfix : m_bugfix;
    final int oBugfix = o.m_isAlpha ? Integer.MIN_VALUE + o.m_bugfix : o.m_bugfix;

    return mBugfix - oBugfix;
  }
}