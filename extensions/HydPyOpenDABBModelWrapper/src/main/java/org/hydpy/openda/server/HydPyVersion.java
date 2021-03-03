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

import org.apache.commons.lang3.StringUtils;

/**
 * @author Gernot Belger
 */
final class HydPyVersion implements Comparable<HydPyVersion>
{
  private final int m_major;

  private final int m_minor;

  private final int m_qualifier;

  private final boolean m_isAlpha;

  public static HydPyVersion parse( final String versionText ) throws HydPyServerException
  {
    final String[] split = StringUtils.splitByCharacterType( versionText );
    if( split.length != 5 || !split[1].equals( "." ) )
      throw new HydPyServerException( String.format( "Received invalid version string: %s", versionText ) );

    boolean isAlpha;
    if( split[3].equals( "." ) )
      isAlpha = false;
    else if( split[3].equals( "a" ) )
      isAlpha = true;
    else
      throw new HydPyServerException( String.format( "Received invalid version string: %s", versionText ) );

    final int major = Integer.parseInt( split[0] );
    final int minor = Integer.parseInt( split[2] );
    final int qualifier = Integer.parseInt( split[4] );

    return new HydPyVersion( major, minor, qualifier, isAlpha );
  }

  public HydPyVersion( final int major, final int minor, final int qualifier, final boolean isAlpha )
  {
    m_major = major;
    m_minor = minor;
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
    buffer.append( m_qualifier );
    return buffer.toString();
  }

  @Override
  public int compareTo( final HydPyVersion o )
  {
    final int majorComp = m_major - o.m_major;
    if( majorComp != 0 )
      return majorComp;

    final int minorComp = m_minor - o.m_minor;
    if( minorComp != 0 )
      return minorComp;

    /* make alpha version umbers very small, so are always less than non-alpha versions */
    final int mQualifier = m_isAlpha ? Integer.MIN_VALUE + m_qualifier : m_qualifier;
    final int oQualifier = o.m_isAlpha ? Integer.MIN_VALUE + o.m_qualifier : o.m_qualifier;

    return mQualifier - oQualifier;
  }
}