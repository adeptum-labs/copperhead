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

package org.copperhead.toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrerequisitesTest extends ScratchTest {
	private static final Function<String, Optional<Path>> NOTHING = program -> Optional.empty();
	private static final Function<String, Optional<Path>> EVERYTHING =
		program -> Optional.of(Path.of("/usr/bin", program));
	private static final String APT = "make, cc, c++ not found; run sudo apt install build-essential";
	private static final String DNF = "make, cc, c++ not found; run sudo dnf install gcc gcc-c++ make";
	private static final String ANY = "make, cc, c++ not found; install make, cc and c++";

	@Example
	public void shouldAskForNothingWhenMakeAndBothCompilersAreThere() throws IOException {
		assertEquals(Optional.empty(), new Prerequisites(EVERYTHING, release("ID=debian\n")).missing());
	}

	@Example
	public void shouldNameOnlyWhatIsMissing() throws IOException {
		final Function<String, Optional<Path>> noCxx = program -> "c++".equals(program) ? Optional.empty()
			: EVERYTHING.apply(program);

		assertEquals(Optional.of("c++ not found; run sudo apt install build-essential"),
			new Prerequisites(noCxx, release("ID=debian\n")).missing());
	}

	@Example
	public void shouldAdviseAptOnDebian() throws IOException {
		assertEquals(Optional.of(APT), missing("PRETTY_NAME=\"Debian GNU/Linux 12 (bookworm)\"\nID=debian\n"));
	}

	@Example
	public void shouldAdviseAptOnUbuntu() throws IOException {
		assertEquals(Optional.of(APT), missing("NAME=\"Ubuntu\"\nID=ubuntu\nID_LIKE=debian\n"));
	}

	@Example
	public void shouldAdviseAptOnLinuxMint() throws IOException {
		assertEquals(Optional.of(APT), missing("NAME=\"Linux Mint\"\nID=linuxmint\nID_LIKE=\"ubuntu debian\"\n"));
	}

	@Example
	public void shouldAdviseDnfOnFedora() throws IOException {
		assertEquals(Optional.of(DNF), missing("NAME=\"Fedora Linux\"\nID=fedora\nVERSION_ID=42\n"));
	}

	@Example
	public void shouldNameTheProgramsOnAnUnknownSystem() throws IOException {
		assertEquals(Optional.of(ANY), missing("NAME=\"Arch Linux\"\nID=arch\n"));
		assertEquals(Optional.of(ANY), new Prerequisites(NOTHING, scratch().resolve("absent")).missing());
	}

	private Optional<String> missing(final String osRelease) throws IOException {
		return new Prerequisites(NOTHING, release(osRelease)).missing();
	}

	private Path release(final String text) throws IOException {
		return Files.writeString(scratch().resolve("os-release"), text);
	}
}
