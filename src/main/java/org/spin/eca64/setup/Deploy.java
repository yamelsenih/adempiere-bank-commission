/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2015 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpya.com                                  *
 *****************************************************************************/
package org.spin.eca64.setup;

import java.util.Properties;

import org.compiere.model.MBankStatementMatcher;
import org.compiere.model.Query;
import org.erpya.eca64.bank.matcher.ProductAccountMatcher;
import org.spin.util.ISetupDefinition;

/**
 * Add a Bank Statement Matcher
 * Please rename this class and package
 * @author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 */
public class Deploy implements ISetupDefinition {

	private static final String DESCRIPTION = "Realiza una coincidencia tomando en cuenta los productos bancario configurado en la cuenta bancaria";
	private static final String UUID = "(*AutomaticSetup*)";
	private static final String NAME = "Coincidencia por Producto Bancario";
	private static final int DEFAULT_SEQUENCE = 10;
	
	@Override
	public String doIt(Properties context, String transactionName) {
		//	Add Model Validator
		createBankStatementMatcher(context, transactionName);
		//	financial management
		return "@AD_SetupDefinition_ID@ @Ok@";
	}
	
	/**
	 * Create Matcher
	 * @param context
	 * @param transactionName
	 * @return
	 */
	private MBankStatementMatcher createBankStatementMatcher(Properties context, String transactionName) {
		MBankStatementMatcher matcher = new Query(context, MBankStatementMatcher.Table_Name, MBankStatementMatcher.COLUMNNAME_Classname + " = ?", transactionName)
				.setParameters(ProductAccountMatcher.class.getName())
				.setClient_ID()
				.<MBankStatementMatcher>first();
		//	Validate
		if(matcher != null
				&& matcher.getC_BankStatementMatcher_ID() > 0) {
			return matcher;
		}
		//	
		matcher = new MBankStatementMatcher(context, 0, transactionName);
		matcher.setAD_Org_ID(0);
		matcher.setName(NAME);
		matcher.setDescription(DESCRIPTION);
		matcher.setSeqNo(DEFAULT_SEQUENCE);
		matcher.setClassname(ProductAccountMatcher.class.getName());
		matcher.setUUID(UUID);
		matcher.setIsDirectLoad(true);
		matcher.saveEx();
		return matcher;
	}
}
