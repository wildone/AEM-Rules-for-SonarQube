package com.cognifide.aemrules.checks;

import org.junit.Test;

public class ResourceResolverShouldBeClosedTest extends AbstractBaseTest {

	@Test
	public void checkInjectorNotClosedInFinallyBlock() {
		check = new ResourceResolverShouldBeClosed();
		filename = "src/test/files/checks/SampleServlet.java";
		verify();
	}

	@Test
	public void checkResourceResolverNotClosedInFinallyBlockWhenResourceResolverComesFromDifferentClass() {
		check = new ResourceResolverShouldBeClosed();
		filename = "src/test/files/checks/ResourceResolverConsumer.java";
		verifyNoIssues();
	}

}
