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
 *     HUMBOLDT EU Integrated Project #030962
 *     Data Harmonisation Panel <http://www.dhpanel.eu>
 */
package eu.esdihumboldt.hale.ui.function;

import org.eclipse.jface.wizard.IWizard;

import eu.esdihumboldt.hale.common.align.model.MutableCell;

/**
 * Function wizard interface
 * 
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 */
public interface FunctionWizard extends IWizard {

	/**
	 * Initialize the wizard. Is called after wizard construction.
	 */
	public void init();

	/**
	 * Get the result cell
	 * 
	 * @return the result cell
	 */
	public MutableCell getResult();

}
