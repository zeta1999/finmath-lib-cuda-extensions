/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 15.06.2016
 */

package net.finmath.montecarlo.assetderivativevaluation;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionCudaWithHostRandomVariable;
import net.finmath.montecarlo.BrownianMotionCudaWithRandomVariableCuda;
import net.finmath.montecarlo.BrownianMotionInterface;
import net.finmath.montecarlo.BrownianMotionJavaRandom;
import net.finmath.montecarlo.RandomVariableCuda;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.model.AbstractModel;
import net.finmath.montecarlo.process.AbstractProcess;
import net.finmath.montecarlo.process.ProcessEulerScheme;
import net.finmath.stochastic.RandomVariableInterface;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationInterface;

/**
 * @author Christian Fries
 *
 */
@RunWith(Parameterized.class)
public class MonteCarloBlackScholesModelTest {

	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
				{ "BrownianMotion" },							// Text case 1: Java implementation
				{ "BrownianMotionJavaRandom" },					// Text case 2: Java implementation
				{ "BrownianMotionCudaWithHostRandomVariable" },	// Text case 3: Java implementation
				{ "BrownianMotionCudaWithRandomVariableCuda" }	// Text case 4: Java implementation			
		});
	}

	static final DecimalFormat formatterReal2	= new DecimalFormat(" 0.00");
	static final DecimalFormat formatterReal4	= new DecimalFormat(" 0.0000");
	static final DecimalFormat formatterSci4	= new DecimalFormat(" 0.0000E00;-0.0000E00");
	static final DecimalFormat formatterSci1	= new DecimalFormat(" 0E00;-0.E00");

	// Model properties
	private final double	initialValue   = 1.0;
	private final double	riskFreeRate   = 0.05;
	private final double	volatility     = 0.30;

	// Process discretization properties
	private final int		numberOfPaths		= 5000000;
	private final int		numberOfTimeSteps	= 10;
	private final double	deltaT				= 1.0;
	
	private final int		seed				= 31415;

	// Product properties
	private final int		assetIndex = 0;
	private final double	optionMaturity = 2.0;
	private final double	optionStrike = 1.05;

	private String testCase;
	private BrownianMotionInterface brownian;

	public MonteCarloBlackScholesModelTest(String testCase) {
		this.testCase = testCase;
		System.out.println(testCase);
	}

	@Before
	public void setUp() {
		// Create a time discretizeion
		TimeDiscretizationInterface timeDiscretization = new TimeDiscretization(0.0 /* initial */, numberOfTimeSteps, deltaT);

		switch(testCase) {
		case "BrownianMotion":
		default:
			brownian = new BrownianMotion(timeDiscretization, 1, numberOfPaths, seed,
					new RandomVariableFactory(true));
			break;
		case "BrownianMotionJavaRandom":
			brownian = new BrownianMotionJavaRandom(timeDiscretization, 1, numberOfPaths, seed,
					new RandomVariableFactory(true));
			break;
		case "BrownianMotionCudaWithHostRandomVariable":
			brownian = new BrownianMotionCudaWithHostRandomVariable(
					timeDiscretization,
					1,
					numberOfPaths,
					seed
					);
			break;
		case "BrownianMotionCudaWithRandomVariableCuda":
			brownian = new BrownianMotionCudaWithRandomVariableCuda(
					timeDiscretization,
					1,
					numberOfPaths,
					seed
					);
			break;
		}
	}

	@Before
	public void cleanUp() {
		RandomVariableCuda.clean();
	}
	
	@Test
	public void testProductImplementation() throws CalculationException {
		long millisStart = System.currentTimeMillis();

		// Create a model
		AbstractModel model = new BlackScholesModel(initialValue, riskFreeRate, volatility);

		// Create a corresponding MC process
		AbstractProcess process = new ProcessEulerScheme(brownian);

		// Link model and process for delegation
		process.setModel(model);
		model.setProcess(process);

		/*
		 * Value a call option - directly
		 */
		TimeDiscretizationInterface timeDiscretization = brownian.getTimeDiscretization();
		
		RandomVariableInterface asset = process.getProcessValue(timeDiscretization.getTimeIndex(optionMaturity), assetIndex);
		RandomVariableInterface numeraireAtPayment = model.getNumeraire(optionMaturity);
		RandomVariableInterface numeraireAtEval = model.getNumeraire(0.0);
		
		RandomVariableInterface payoff = asset.sub(optionStrike).floor(0.0);
		double value = payoff.div(numeraireAtPayment).mult(numeraireAtEval).getAverage();

		double valueAnalytic = AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, volatility, optionMaturity, optionStrike);

		System.out.print("   value Monte-Carlo = " + formatterReal4.format(value));
		System.out.print("\t value analytic    = " + formatterReal4.format(valueAnalytic));

		long millisEnd = System.currentTimeMillis();

		System.out.println("\t calculation time = " + formatterReal2.format((millisEnd - millisStart)/1000.0) + " sec.");
		System.out.println("");

		Assert.assertEquals(valueAnalytic, value, 0.005);
	}
}
