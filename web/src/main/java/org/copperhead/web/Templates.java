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

package org.copperhead.web;

import java.util.Set;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.copperhead.web.action.View;

public class Templates {
	private static final String PREFIX = "templates/";
	private static final String SUFFIX = ".html";
	private static final String SELECTOR = "::";
	private static final String VARIABLE = "view";

	private final TemplateEngine engine = new TemplateEngine();

	public Templates(final boolean cache) {
		final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();

		resolver.setPrefix(PREFIX);
		resolver.setSuffix(SUFFIX);
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");

		/* Development switches caching off so an edited template appears on the next
		   request, which is the entire point of building the interface in a browser. */
		resolver.setCacheable(cache);
		engine.setTemplateResolver(resolver);
	}

	/**
	 * Renders what the view names, the way Thymeleaf names it: {@code page} or {@code page :: block}.
	 * The view itself is what the template reads as {@code view}.
	 */
	public String render(final View view) {
		final String specification = view.template();
		final int separator = specification.indexOf(SELECTOR);
		final Context context = new Context();

		context.setVariable(VARIABLE, view);

		if (separator < 0) {
			return engine.process(specification.trim(), context);
		}

		return engine.process(specification.substring(0, separator).trim(),
			Set.of(specification.substring(separator + SELECTOR.length()).trim()), context
		);
	}
}
