/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.peaks.CouplingItem;

public class PeakFitTest {

    double[] a = {2, 15, 10, 0.05, 2, 59, 10, 0.05};
    double[] tol = {1.0e-2, 1.0e-2, 1.0e-2, 0.02, 1.0e-2, 1.0e-2, 1.0e-2, 0.02};
    double[] start = {2.2, 12, 9, 0.01, 1.8, 54.0, 13.0, 0.02};
    double[] lower = {1, 10, 8, 0.0, 1, 50, 8, 0.0};
    double[] upper = {3, 19, 15, 0.2, 3.0, 70, 16, 0.2};

    double ftol = 0.01;
    double[] aa = {
        2, 1.0, 15, 10, 0.05,
        2.3, 2.0, 59, 10, 0.05};
    double[] atol = {ftol, ftol, ftol, ftol, 0.001, ftol, ftol, ftol, ftol, 0.001};
    double[] astart = {2.2, 0.9, 12, 9, 0.01, 1.8, 2.8, 54, 13, 0.02};
    double[] alower = {1, 0.0, 10, 8, -0.2, 0.1, 0.0, 50, 8, 0.0};
    double[] aupper = {3, 2.0, 19, 15, 0.2, 4.0, 5.0, 70, 16, 0.2};

    void setupTwoSigsFitAmp(PeakFit peakFit) {
        int n = 100;

        CouplingItem[][] cplItems = new CouplingItem[aa.length / 4][1];
        double[] amps = new double[cplItems.length];
        for (int i = 0; i < cplItems.length; i++) {
            amps[i] = 1.0;
            cplItems[i][0] = new CouplingItem(0, 2);
        }
        peakFit.initTest(n);
        peakFit.setSignals(cplItems);

        RealVector ampVector = new ArrayRealVector(amps);
        peakFit.simulate(aa, ampVector, 0.001);
        peakFit.setOffsets(astart, alower, aupper);

    }

    void setupTwoSigsAmp(PeakFit peakFit) {
        int n = 100;

        // lw f   j   slope
        CouplingItem[][] cplItems = new CouplingItem[a.length / 4][1];
        double[] amps = new double[cplItems.length];
        for (int i = 0; i < cplItems.length; i++) {
            amps[i] = 1.0;
            cplItems[i][0] = new CouplingItem(0, 2);
        }
        peakFit.initTest(n);
        peakFit.setSignals(cplItems);

        RealVector ampVector = new ArrayRealVector(amps);
        peakFit.simulate(a, ampVector, 0.001);
        peakFit.setOffsets(start, lower, upper);

    }

    @Test
    public void testFitWithLinearAmpsBOBYQA() throws Exception {
        int nSteps = 1000;
        int nDim = start.length;
        PeakFit peakFit = new PeakFit(false);
        setupTwoSigsAmp(peakFit);
        int nInterpolationPoints = (nDim + 1) * (nDim + 2) / 2;
        peakFit.optimizeBOBYQA(nSteps, nInterpolationPoints);
        double[] best = peakFit.getBestPoint();
        for (int i = 0; i < best.length; i++) {
            Assert.assertEquals(a[i], best[i], tol[i]);
        }
    }

    @Test
    public void testFitWithLinearAmpsCMAES() throws Exception {
        int nSteps = 500;
        int nDim = start.length;
        PeakFit peakFit = new PeakFit(false);
        setupTwoSigsAmp(peakFit);
        peakFit.optimizeCMAES(nSteps);
        double[] best = peakFit.getBestPoint();
        for (int i = 0; i < best.length; i++) {
            Assert.assertEquals(a[i], best[i], tol[i]);
        }
    }

    @Test
    public void testFitCMAES() throws Exception {
        int nDim = astart.length;
        int nSteps = 400;
        PeakFit peakFit = new PeakFit(true);
        setupTwoSigsFitAmp(peakFit);
        peakFit.dumpSignals();
        peakFit.optimizeCMAES(nSteps);
        double[] best = peakFit.getBestPoint();
        System.out.println("done");
        for (int i = 0; i < best.length; i++) {
            Assert.assertEquals(aa[i], best[i], atol[i]);
        }
    }
}
