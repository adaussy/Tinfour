/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
 */

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 03/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.examples;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.SimpleTimeZone;
import tinfour.common.IIncrementalTin;
import tinfour.common.Vertex;
import tinfour.gwr.BandwidthSelectionMethod;
import tinfour.gwr.SurfaceGwr;
import tinfour.gwr.SurfaceModel;
import tinfour.test.utils.IDevelopmentTest;
import tinfour.test.utils.TestOptions;
import tinfour.test.utils.VertexLoader;
import tinfour.interpolation.GwrTinInterpolator;
import tinfour.utils.TinInstantiationUtility;

/**
 * Provides an example of code to build a GRID from an LAS file
 */
public class ExampleGWR implements IDevelopmentTest {

  static String[] mandatoryOptions = {
    "-in"
  };

  /**
   * Provides the main method for an example application
   * that develops raster elevation files in Esri's ASCII format
   * and image files in PNG format.
   * <p>
   * Data is accepted from an LAS file. For best results, the file
   * should be in a projected coordinate system rather than a geographic
   * coordinate system. In general, geographic coordinate systems are a
   * poor choice for Lidar data processing since they are non-isotropic,
   * however many data sources provide them in this form.
   * <p>
   * Command line arguments include the following:
   * <pre>
   *   -in &lt;file path&gt;    input LAS file
   *   -frame xmin xmax ymin ymax frame for processing.
   *
   *    Other arguments used by Tinfour test programs are supported
   * </pre>
   *
   * @param args command line arguments indicating the input LAS file
   * for processing and various output options.
   */
  public static void main(String[] args) {
    ExampleGWR example = new ExampleGWR();

    try {
      example.runTest(System.out, args);
    } catch (IOException | IllegalArgumentException ex) {
      ex.printStackTrace(System.err);
    }
  }

  /**
   * Run the example code accepting an input LAS file and writing an
   * output grid in Esri's ASCII raster format.
   *
   * @param ps a valid print-stream for recording results of processing.
   * @param args a set of arguments for configuring the processing.
   * @throws IOException if unable to read input or write output files.
   */
  @Override
  public void runTest(PrintStream ps, String[] args) throws IOException {
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm");
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.println("Example Use of Geographically Weighted Regression (GWR)\n");
    ps.format("Date/time of test: %s (UTC)\n", sdFormat.format(date));

    TestOptions options = new TestOptions();

    boolean[] optionsMatched = options.argumentScan(args);
    options.checkForMandatoryOptions(args, mandatoryOptions);

    // if any non-recognized options were supplied, complain
    options.checkForUnrecognizedArgument(args, optionsMatched);

    // Load Vertices from LAS file ------------------------------------
    //   The vertex loader implements logic to use test options such as
    // those that indicate Lidar classification for processing
    // (ground points only, etc.) and sorting options.
    File inputFile = options.getInputFile();
    ps.format("Input file: %s\n", inputFile.getAbsolutePath());
    VertexLoader loader = new VertexLoader();
    List<Vertex> vertexList = loader.readInputFile(options);
    int nVertices = vertexList.size();
    ps.format("Number of vertices: %8d\n", nVertices);
    double xmin = loader.getXMin();
    double xmax = loader.getXMax();
    double ymin = loader.getYMin();
    double ymax = loader.getYMax();

    double area = (xmax - xmin) * (ymax - ymin);
    double sSpace = 0.87738 * Math.sqrt(area / nVertices);
    double nominalPointSpacing = sSpace; //used as an input into TIN class/

    ps.println("Building TIN");
    TinInstantiationUtility tiu = new TinInstantiationUtility(0.5, nVertices);
    IIncrementalTin tin = tiu.constructInstance(nominalPointSpacing);
    tin.add(vertexList, null);

    ps.println("Performing Regression");

    double x = (xmin + xmax) / 2.0;
    double y = (ymin + ymax) / 2.0;
    x = xmin+20;
    y = ymax-200;

    // Perform the interpolation for coordinates (x,y) given a valid TIN
    // using a specified surface model and the Adaptive Bandwidth selection
    GwrTinInterpolator regInterpolator = new GwrTinInterpolator(tin);
    double z = regInterpolator.interpolate(
      SurfaceModel.Cubic,
      BandwidthSelectionMethod.AdaptiveBandwidth, 1.0,
      x, y, null);

    // Obtain the SurfaceGWR for the most recent interpolation,
    // extract some statistics and results, and output to a print stream.
    SurfaceGwr gwr = regInterpolator.getCurrentSurfaceGWR();
    double[] beta = gwr.getCoefficients();
    double[] predictionInterval = gwr.getPredictionInterval(0.05);

    double zX = beta[1];
    double zY = beta[2];
    double zXX = 2 * beta[3];
    double zYY = 2 * beta[4];
    double zXY = beta[4];
    double azimuth = Math.atan2(zY, zX);
    double compass = Math.toDegrees(Math.atan2(zX, zY));
    if (compass < 0) {
      compass += 360;
    }
    double grade = Math.sqrt(zX * zX + zY * zY);
    double slope = Math.toDegrees(Math.atan(grade));
    double kP = (zXX * zX * zX + 2 * zXY * zX * zY + zYY * zY * zY)
      / ((zX * zX + zY * zY) * Math.pow(zX * zX + zY * zY + 1.0, 1.5));

    ps.format("Interpolation x:                   %10.1f\n", x);
    ps.format("Interpolation y:                   %10.1f\n", y);
    ps.format("Estimated z:                          %12.5f\n", z);
    ps.format("Prediction interval (95%% confidence): %12.5f to %6.5f   (%f)\n",
      predictionInterval[0], predictionInterval[1],
      predictionInterval[1] - predictionInterval[0]);
    ps.format("Zx:                                   %12.5f\n", beta[1]);
    ps.format("Zy:                                   %12.5f\n", beta[2]);
    ps.format("Azimuth steepest ascent               %12.5f\n", azimuth);
    ps.format("Compass bearing steepest ascent          %05.1f\u00b0\n", compass);
    ps.format("Grade                                 %8.1f%%\n", grade * 100);
    ps.format("Slope:                                %8.1f\u00b0\n", slope);
    ps.format("Profile curvature:                    %12.5f\n", kP);
    ps.format("Eff deg of freedom:                   %12.5f\n", gwr.getEffectiveDegreesOfFreedom());
    ps.format("Variance of Residuals                 %12.5f\n", gwr.getResidualVariance());
    ps.format("Bandwidth                             %12.5f\n", regInterpolator.getBandwidth());
  }

}
