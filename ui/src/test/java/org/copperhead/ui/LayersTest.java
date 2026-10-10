/*
    Copperhead
    Copyright © 2026 Adam Waldenberg and Adeptum AB

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.ui;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import net.jqwik.api.Example;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Which module may know about which, so the split survives more than the first commit. */
public class LayersTest {
	private static final JavaClasses CLASSES = new ClassFileImporter()
		.withImportOption(new ImportOption.DoNotIncludeTests())
		.importPackages("org.copperhead");

	@Example
	public void shouldKeepViewsFreeOfTheModelAndTheServer() {
		noClasses().that().resideInAPackage("..copperhead.ui.view..")
			.should().dependOnClassesThat().resideInAnyPackage("..copperhead.core..", "org.eclipse.jetty..")
			.check(CLASSES);
	}

	@Example
	public void shouldKeepTheInterfaceOffTheServer() {
		noClasses().that().resideInAPackage("..copperhead.ui..")
			.should().dependOnClassesThat().resideInAPackage("org.eclipse.jetty..")
			.check(CLASSES);
	}

	@Example
	public void shouldKeepTheModelUnawareOfTheInterface() {
		noClasses().that().resideInAPackage("..copperhead.core..")
			.should().dependOnClassesThat().resideInAnyPackage("..copperhead.web..", "..copperhead.ui..")
			.check(CLASSES);
	}

	@Example
	public void shouldKeepTheModelOffTheDiskTheNetworkAndOtherProcesses() {
		noClasses().that().resideInAPackage("..copperhead.core..")
			.should().dependOnClassesThat().resideInAnyPackage("java.nio.file..", "java.net..")
			.orShould().dependOnClassesThat().belongToAnyOf(ProcessBuilder.class)
			.check(CLASSES);
	}

	@Example
	public void shouldKeepViewsOffTheToolchain() {
		noClasses().that().resideInAPackage("..copperhead.ui.view..")
			.should().dependOnClassesThat().resideInAPackage("..copperhead.toolchain..")
			.check(CLASSES);
	}

	@Example
	public void shouldKeepTheToolchainUnawareOfTheInterfaceAndTheServer() {
		noClasses().that().resideInAPackage("..copperhead.toolchain..")
			.should().dependOnClassesThat().resideInAnyPackage("..copperhead.ui..", "..copperhead.web..")
			.check(CLASSES);
	}
}
