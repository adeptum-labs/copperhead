/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.shell;

import java.awt.Image;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * The Copperhead logo at the sizes window managers and task bars ask for. Each size is drawn
 * ahead of time because scaling the large logo down at run time leaves the small ones blurred.
 */
public final class WindowIcon {
	private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

	private WindowIcon() {
	}

	public static List<Image> images() {
		return IntStream.of(SIZES).mapToObj(WindowIcon::read).toList();
	}

	private static Image read(final int size) {
		try {
			return ImageIO.read(WindowIcon.class.getResource("icons/copperhead-%d.png".formatted(size)));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
