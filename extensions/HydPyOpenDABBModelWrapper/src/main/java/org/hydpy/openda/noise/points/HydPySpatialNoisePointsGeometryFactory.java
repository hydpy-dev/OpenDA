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
package org.hydpy.openda.noise.points;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.hydpy.openda.noise.ISpatialNoiseGeometry;
import org.hydpy.openda.noise.ISpatialNoiseGeometryFactory;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.hydpy.openda.server.HydPyModelInstance;
import org.hydpy.openda.server.HydPyServerManager;
import org.openda.utils.ConfigTree;
import org.openda.utils.io.CsvReader;

/**
 * Implementation of {@link ISpatialNoiseGeometryFactory} for an unordered list
 * of (point-like) locations. Noise is generated by assuming a Gaussian
 * distribution and a distance based correlation. The point locations will be
 * read from a separate configuration file, while their order (within the state
 * vector) will be determined by calling a HydPy server.
 *
 * @author Gernot Belger
 */
public final class HydPySpatialNoisePointsGeometryFactory implements ISpatialNoiseGeometryFactory
{
  @Override
  public ISpatialNoiseGeometry create( final ConfigTree config, final File workingDir, final CoordinatesType coordinatesType, final double horizontalCorrelationScale )
  {
    // corresponding exchange itemId of HydPy.
    final String itemId = config.getAsString( "@modelExchangeItem", null );

    // file with element locations
    final String locationsFile = config.getAsString( "@locationsFile", null );
    if( StringUtils.isBlank( locationsFile ) )
      throw new RuntimeException( "Argument 'locationsFile' must be specified." );
    final Map<String, Vector2D> locations = readLocations( workingDir, locationsFile );

    // fetch item order from HydPy
    final Pair<double[], double[]> coordinates = fetchOrderedCoordinates( itemId, locations );

    final double[] x = coordinates.getLeft();
    final double[] y = coordinates.getRight();
    return new SpatialNoisePointsGeometry( coordinatesType, horizontalCorrelationScale, x, y );
  }

  private Map<String, Vector2D> readLocations( final File workingDir, final String locationsPath )
  {
    final Map<String, Vector2D> locations = new HashMap<>();

    final File locationsFile = new File( workingDir, locationsPath );

    try( final CsvReader csvReader = new CsvReader( locationsFile ) )
    {
      csvReader.setColumnSeparatorChar( '\t' );
      csvReader.setCommentLinePrefix( "#" );
      csvReader.setQuoteChar( '"' );

      while( true )
      {
        final String[] values = csvReader.readCSVLineTrimElements();
        if( values == null )
          return locations;

        final int lineNumber = csvReader.getLineNumber();

        if( values.length < 3 )
        {
          final String message = String.format( "%s - Line %d: all lines must have at least 3 columns", locationsPath, lineNumber );
          throw new RuntimeException( message );
        }

        final String elementName = values[0];
        final double xCoord = parseDouble( values[1], locationsPath, lineNumber );
        final double yCoord = parseDouble( values[2], locationsPath, lineNumber );

        final Vector2D point = new Vector2D( xCoord, yCoord );
        locations.put( elementName, point );
      }
    }
    catch( final IOException e )
    {
      throw new RuntimeException( e );
    }
  }

  private double parseDouble( final String text, final String locationsPath, final int lineNumber )
  {
    try
    {
      return Double.parseDouble( text );
    }
    catch( final Exception e )
    {
      final String message = String.format( "%s - Line %d: invalid coordinate value: %s", locationsPath, lineNumber, text );
      throw new RuntimeException( message, e );
    }
  }

  private Pair<double[], double[]> fetchOrderedCoordinates( final String itemId, final Map<String, Vector2D> locations )
  {
    final String[] elementNames = fetchOrderedElements( itemId );

    final double[] x = new double[elementNames.length];
    final double[] y = new double[elementNames.length];

    final Set<String> missingNames = new HashSet<>();

    for( int i = 0; i < elementNames.length; i++ )
    {
      final String elementName = elementNames[i];
      final Vector2D point = locations.get( elementName );

      if( point == null )
      {
        if( missingNames.isEmpty() )
          System.err.format( "Missing elements in locations file for item '%s':%n", itemId );
        System.err.print( '\t' );
        System.err.println( elementName );
      }

      x[i] = point.getX();
      y[i] = point.getY();
    }

    if( !missingNames.isEmpty() )
      throw new RuntimeException( String.format( "Missing elements in locations file for item '%s':%n", itemId ) );

    return Pair.of( x, y );
  }

  private String[] fetchOrderedElements( final String itemId )
  {
    try
    {
      final HydPyModelInstance hydPyInstance = HydPyServerManager.instance().getOrCreateInstance( HydPyServerManager.ANY_INSTANCE );
      return hydPyInstance.getItemNames( itemId );
    }
    catch( final Exception e )
    {
      throw new RuntimeException( String.format( "Failed to fetch ordered element list for item '%s' from HydPy", itemId ), e );
    }
  }
}