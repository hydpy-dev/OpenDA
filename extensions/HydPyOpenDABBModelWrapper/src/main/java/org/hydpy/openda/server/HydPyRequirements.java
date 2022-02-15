/**
 * Copyright (c) 2021 by
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

import java.io.PrintStream;

import org.openda.utils.VersionUtils;

/**
 * Contains requirements of the wrapper.
 *
 * @author Gernot Belger
 */
final class HydPyRequirements
{
  // FIXME: change!
  public final static Version VERSION_HYDPY_SUPPORTED = new Version( 4, 1, 0, null, true );

  public final static Version VERSION_OPENDA_SUPPORTED = new Version( 3, 0, 0, null, false );

  public static void checkHydPyVersion( final Version current, final PrintStream debugOut )
  {
    if( current.compareTo( VERSION_HYDPY_SUPPORTED ) < 0 )
      System.err.format( "WARNING: HydPy Version of Server (%s) is LESS than the supported vrsion (%s) of this wrapper, do expect compatibility problems.%n", current, VERSION_HYDPY_SUPPORTED );
    else if( current.compareTo( VERSION_HYDPY_SUPPORTED ) > 0 )
      debugOut.format( "INFO: HydPy Version of Server (%s) is GREATER than the supported vrsion (%s) of this wrapper%n", current, VERSION_HYDPY_SUPPORTED );
  }

  public static void checkOpenDaVersion( final PrintStream debugOut )
  {
    try
    {
      /* find OpenDA Version */
      // REMARK: this will get the version from the manifest of the openda_core.jar
      final String versionString = VersionUtils.getVersionFromManifest( VersionUtils.class );
      final Version current = Version.parse( versionString );

      if( current.compareTo( VERSION_OPENDA_SUPPORTED ) < 0 )
        System.err.format( "WARNING: OpenDA Version (%s) is LESS than the supported vrsion (%s) of this wrapper, do expect compatibility problems.%n", current, VERSION_OPENDA_SUPPORTED );
      else if( current.compareTo( VERSION_OPENDA_SUPPORTED ) > 0 )
        debugOut.format( "INFO: OpenDA Version (%s) is GREATER than the supported vrsion (%s) of this wrapper%n", current, VERSION_OPENDA_SUPPORTED );
    }
    catch( final HydPyServerException e )
    {
      System.err.format( "WARNING: Failed to determine OpenDA Version. The supported vrsion is %s%n", VERSION_OPENDA_SUPPORTED );
    }
  }
}