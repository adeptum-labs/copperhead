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

package org.copperhead.web.action;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects every {@link Action} the container sees while it starts, so that adding one is a matter
 * of annotating a method rather than remembering to register it somewhere else as well.
 */
public class ActionExtension implements Extension {
	private final Map<String, Method> found = new LinkedHashMap<>();

	<T> void collect(@Observes final ProcessAnnotatedType<T> event) {
		for (final AnnotatedMethod<? super T> method : event.getAnnotatedType().getMethods()) {
			Actions.bind(found, method.getJavaMember());
		}
	}

	public Map<String, Method> found() {
		return Map.copyOf(found);
	}
}
