/*
 * Copyright (c) 2012 Data Harmonisation Panel
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Data Harmonisation Panel <http://www.dhpanel.eu>
 */

package eu.esdihumboldt.hale.server.templates.war;

import org.apache.wicket.Page;
import org.apache.wicket.model.Model;

import de.agilecoders.wicket.core.markup.html.bootstrap.navbar.Navbar;
import de.agilecoders.wicket.core.markup.html.bootstrap.navbar.Navbar.ComponentPosition;
import de.agilecoders.wicket.core.markup.html.bootstrap.navbar.NavbarButton;
import de.agilecoders.wicket.core.markup.html.bootstrap.navbar.NavbarComponents;
import eu.esdihumboldt.hale.server.templates.war.pages.EditTemplatePage;
import eu.esdihumboldt.hale.server.templates.war.pages.MyTemplatesPage;
import eu.esdihumboldt.hale.server.templates.war.pages.TemplatePage;
import eu.esdihumboldt.hale.server.templates.war.pages.TemplatesPage;
import eu.esdihumboldt.hale.server.templates.war.pages.UpdateTemplatePage;
import eu.esdihumboldt.hale.server.templates.war.pages.UploadTemplatePage;
import eu.esdihumboldt.hale.server.webapp.BaseWebApplication;

/**
 * Application for managing and accessing project templates.
 * 
 * @author Simon Templer
 */
public class TemplatesApplication extends BaseWebApplication {

	@Override
	public Class<? extends Page> getHomePage() {
		return TemplatesPage.class;
	}

	@Override
	public void init() {
		super.init();

		mountPage("/share", UploadTemplatePage.class);

		mountPage("/show", TemplatePage.class);

		mountPage("/edit", EditTemplatePage.class);

		mountPage("/update", UpdateTemplatePage.class);

		mountPage("/my", MyTemplatesPage.class);
	}

	@Override
	public String getMainTitle() {
		return super.getMainTitle() + " Templates";
	}

	@Override
	public void addNavBarExtras(Navbar navbar, boolean loggedIn) {
		super.addNavBarExtras(navbar, loggedIn);

		if (loggedIn) {
			NavbarButton<Void> my = new NavbarButton<>(MyTemplatesPage.class,
					Model.of("My Templates"));
			navbar.addComponents(NavbarComponents.transform(ComponentPosition.LEFT, my));
		}

		NavbarButton<Void> share = new NavbarButton<>(UploadTemplatePage.class, Model.of("Share"));
		navbar.addComponents(NavbarComponents.transform(ComponentPosition.LEFT, share));
	}

}
