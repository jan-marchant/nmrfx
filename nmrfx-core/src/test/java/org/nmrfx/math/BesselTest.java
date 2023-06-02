package org.nmrfx.math;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author brucejohnson
 */
public class BesselTest {

    // test values are generated with Python using scipy.special.iv(0,r)
    static final double[] TESTDATA = {
        0.4785092050636913, 1.0580671773063608e+00,
        2.5489712796979673, 3.4158633960339158e+00,
        1.6115716858050333, 1.7626057155255828e+00,
        3.9280390341046694, 1.0622068954282815e+01,
        0.1158166082488893, 1.0033561840096783e+00,
        3.1178932773464001, 5.3725290284961984e+00,
        0.3903274714919919, 1.0384531131191963e+00,
        4.7500095641615934, 2.1804083763876044e+01,
        2.8984358579908691, 4.4971020129794592e+00,
        3.5423187579984465, 7.6459108892769674e+00,
        12.0385777653610511, 1.9661915814488759e+04,
        24.2009605274082276, 2.6401525765385137e+09,
        7.1438215513566439, 1.9261979626476872e+02,
        15.6295406791533722, 6.2424195420272730e+05,
        22.1568381813486006, 3.5748427171795541e+08,
        20.0874711885398973, 4.7435000109928124e+07,
        26.9801879042932029, 4.0251792555125099e+10,
        16.3270706094921110, 1.2264408093032311e+06,
        29.4961010213746491, 4.7631527404934106e+11,
        21.4138249346451381, 1.7300807843933165e+08,
        33.1981272961568550, 1.8187685852960867e+13,
        9.4498853621298515, 1.6723195986371154e+03,
        31.5543615925368499, 3.6059036408421899e+12,
        61.4564587686688313, 2.4987215935473173e+25,
        76.4095888032903758, 6.9873757980600205e+31,
        90.6862141938938180, 1.0168661732682538e+38,
        41.6179052793501540, 7.3623601314878688e+16,
        20.2637445009886683, 5.6328922067964852e+07,
        60.9261873990346956, 1.4767722475024693e+25,
        60.7827791913338231, 1.2809907414828653e+25
    };

    @Test
    public void testMolFromSequence() {
        int n = TESTDATA.length;
        double[] relDelta = new double[n];
        double[] zeros = new double[n];
        for (int i = 0; i < n; i += 2) {
            int j = i / 2;
            double x = TESTDATA[i];
            double valid = TESTDATA[i + 1];
            double calc = Bessel.i0(x);
            double delta = valid - calc;
            relDelta[j] = delta / valid;
        }
        Assert.assertArrayEquals(relDelta, zeros, 9.0e-16);
    }

}
