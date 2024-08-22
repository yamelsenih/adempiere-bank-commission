/**************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                               *
 * This program is free software; you can redistribute it and/or modify it    		  *
 * under the terms version 2 or later of the GNU General Public License as published  *
 * by the Free Software Foundation. This program is distributed in the hope           *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied         *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                   *
 * See the GNU General Public License for more details.                               *
 * You should have received a copy of the GNU General Public License along            *
 * with this program; if not, printLine to the Free Software Foundation, Inc.,        *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                             *
 * For the text or an alternative of this public license, you may reach us            *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, S.A. All Rights Reserved.  *
 * Contributor: Yamel Senih ysenih@erpya.com                                          *
 * Contributor: Carlos Parada cparada@erpya.com                                       *
 * See: www.erpya.com                                                                 *
 *************************************************************************************/
package org.erpya.eca64.bank.matcher;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Class used for Test import matcher
 *
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 */
import org.compiere.impexp.BankStatementMatchInfo;
import org.compiere.impexp.BankStatementMatcherInterface;
import org.compiere.model.MBankStatementLine;
import org.compiere.model.MCurrency;
import org.compiere.model.MPayment;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.adempiere.core.domains.models.X_I_BankStatement;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.spin.eca64.util.Changes;

/**
 * Add matcher by reference with like
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 * <li> FR [ 1807 ] Add Match class for Reference No
 * @see https://github.com/adempiere/adempiere/issues/1807
 */
public class ProductAccountMatcher implements BankStatementMatcherInterface {

	public ProductAccountMatcher() {
		
	}

	@Override
	public BankStatementMatchInfo findMatch(MBankStatementLine bsl, List<Integer> includedPayments, List<Integer> exludedPayments) {
		return null;
	}

	@Override
	public BankStatementMatchInfo findMatch(X_I_BankStatement ibs, List<Integer> includedPayments, List<Integer> exludedPayments) {
		StringBuffer paymentWhereClause = new StringBuffer();
		if(includedPayments != null
				&& includedPayments.size() > 0) {
			paymentWhereClause.append(" AND ").append("p.C_Payment_ID").append(" IN").append(includedPayments.toString().replace('[','(').replace(']',')')).append(" ");
		}
		if(exludedPayments != null
				&& exludedPayments.size() > 0) {
			paymentWhereClause.append(" AND ").append("p.C_Payment_ID").append(" NOT IN").append(exludedPayments.toString().replace('[','(').replace(']',')')).append(" ");
		}
		BankStatementMatchInfo info = new BankStatementMatchInfo();
		//	Validate
		if(ibs.getC_Payment_ID() != 0 || ibs.getStmtAmt().signum() <= 0) {
			return info;
		}
		List<Integer> accountProductsIds = getAccountProducts(ibs.getCtx(), ibs.getC_BankAccount_ID());
		if(accountProductsIds == null 
				|| accountProductsIds.size() == 0) {
			return info;
		}
		int precision = -1;
		if(!Util.isEmpty(ibs.getISO_Code())) {
			precision = MCurrency.get(ibs.getCtx(), ibs.getISO_Code()).getStdPrecision();
		} else if(ibs.getC_Currency_ID() != 0) {
			precision = MCurrency.getStdPrecision(ibs.getCtx(), ibs.getC_Currency_ID());
		}
		//	For all Product Accounts
		for(int productAccountId: accountProductsIds) {
//		accountProductsIds.forEach(productAccountId -> {
			PO productAccount = MTable.get(ibs.getCtx(), Changes.TableName_ECA64_BankAccountProduct).getPO(productAccountId, null);
			if(productAccount != null) {
				BigDecimal paymentAmount = getProductPaymentAmount(productAccount, ibs.getStmtAmt(), precision);
				BigDecimal toleranceAmount = Optional.ofNullable((BigDecimal) productAccount.get_Value(Changes.ECA64_ToleranceAmount)).orElse(Env.ZERO);
				int chargeId = productAccount.get_ValueAsInt("C_Charge_ID");
				String referenceNo = productAccount.get_ValueAsString("ReferenceNo");
				boolean matchedFromMovement = false;
				if(!Util.isEmpty(referenceNo)) {
					if(!Util.isEmpty(ibs.getReferenceNo())) {
						matchedFromMovement = ibs.getReferenceNo().toUpperCase().contains(referenceNo.toUpperCase());
					}
					if(!matchedFromMovement && !Util.isEmpty(ibs.getEftReference())) {
						matchedFromMovement = ibs.getEftReference().toUpperCase().contains(referenceNo.toUpperCase());
					}
					if(!matchedFromMovement && !Util.isEmpty(ibs.getDescription())) {
						matchedFromMovement = ibs.getDescription().toUpperCase().contains(referenceNo.toUpperCase());
					}
					if(!matchedFromMovement && !Util.isEmpty(ibs.getMemo())) {
						matchedFromMovement = ibs.getMemo().toUpperCase().contains(referenceNo.toUpperCase());
					}
					if(!matchedFromMovement && !Util.isEmpty(ibs.getEftMemo())) {
						matchedFromMovement = ibs.getEftMemo().toUpperCase().contains(referenceNo.toUpperCase());
					}
					if(!matchedFromMovement && !Util.isEmpty(ibs.getEftCheckNo())) {
						matchedFromMovement = ibs.getEftCheckNo().toUpperCase().contains(referenceNo.toUpperCase());
					}
				}
				if(matchedFromMovement) {
					String ORDERVALUE = " DESC NULLS LAST";
					StringBuffer sql = new StringBuffer("SELECT p.C_Payment_ID "
							+ "FROM C_Payment p "
							+ "WHERE p.AD_Client_ID = ? ");
					if(paymentWhereClause.length() > 0) {
						sql.append(paymentWhereClause);
					}
					//	Were
					StringBuffer where = new StringBuffer();
					StringBuffer orderByClause = new StringBuffer(" ORDER BY ");
					//	Search criteria
					List<Object> params = new ArrayList<Object>();
					//	Client
					params.add(ibs.getAD_Client_ID());
					//	For Amount
					where.append("AND p.PayAmt >= ? AND p.PayAmt <= ?");
					params.add(paymentAmount.subtract(toleranceAmount));
					params.add(paymentAmount.add(toleranceAmount));
					//	For reference
					if(!Util.isEmpty(referenceNo)) {
						where.append(" AND (");
						where.append("UPPER(TRIM(p.DocumentNo)) LIKE '%' || ? || '%'");
						params.add(referenceNo.toUpperCase().trim());
						where.append(" OR UPPER(TRIM(p.Description)) LIKE '%' || ? || '%'");
						params.add(referenceNo.toUpperCase().trim());
						if(!Util.isEmpty(ibs.getReferenceNo())) {
							where.append(" OR UPPER(TRIM(p.DocumentNo)) = ?");
							params.add(ibs.getReferenceNo().toUpperCase().trim());
							where.append(" OR UPPER(TRIM(p.Description)) LIKE '%' || ? || '%'");
							params.add(ibs.getReferenceNo().toUpperCase().trim());
							where.append(" OR ? LIKE '%' || UPPER(TRIM(p.CheckNo)) ");
							params.add(ibs.getReferenceNo().toUpperCase().trim());
							where.append(" OR ? LIKE '%' || UPPER(TRIM(p.DocumentNo)) ");
							params.add(ibs.getReferenceNo().toUpperCase().trim());
						}
						where.append(")");
					}
					//	Add Currency
					if(!Util.isEmpty(ibs.getISO_Code())) {
						where.append(" AND EXISTS(SELECT 1 FROM C_Currency c WHERE c.C_Currency_ID = p.C_Currency_ID AND c.ISO_Code = ?) ");
						params.add(ibs.getISO_Code());
					} else if(ibs.getC_Currency_ID() != 0) {
						where.append(" AND p.C_Currency_ID = ? ");
						params.add(ibs.getC_Currency_ID());
					}
					//	For Amount
					if(where.length() > 0) {
						where.append(" AND ");
					}
					//	Validate amount for it
					boolean isReceipt = ibs.getTrxAmt().compareTo(Env.ZERO) > 0;
					//	Add Receipt
					where.append(" p.IsReceipt = ? ");
					params.add(isReceipt);
					//	For Account
					if(where.length() > 0) {
						where.append(" AND ");
					}
					where.append("(p.C_BankAccount_ID = ?)");
					params.add(ibs.getC_BankAccount_ID());
					//	Additional validation
					where.append(" AND p.DocStatus IN('CO', 'CL')");
					where.append(" AND p.IsReconciled = 'N'");
					where.append(" AND NOT EXISTS(SELECT 1 FROM I_BankStatement i WHERE i.C_Payment_ID = p.C_Payment_ID) ");
					//	Add Order By
					orderByClause.append("p.DateTrx ASC");
					orderByClause.append(", p.DocumentNo").append(ORDERVALUE);
					orderByClause.append(", p.CheckNo").append(ORDERVALUE);
					orderByClause.append(", p.Description").append(ORDERVALUE);
					//	Add where
					sql.append(where);
					//	Add Order By
					sql.append(orderByClause);
					//	Find payment
					int paymentId = DB.getSQLValue(ibs.get_TrxName(), sql.toString(), params);
					//	set if exits
					if(paymentId > 0) {
						info.setC_Payment_ID(paymentId);
						MPayment payment = new MPayment(ibs.getCtx(), paymentId, ibs.get_TrxName());
						ibs.setTrxAmt(payment.getPayAmt(false));
						//	Calculate difference
						BigDecimal difference = ibs.getStmtAmt().subtract(payment.getPayAmt(false));
						//	For charge
						if(productAccount.get_ValueAsBoolean(Changes.ECA64_IsApplyCharge)) {
							BigDecimal chargePercentage = Optional.ofNullable((BigDecimal) productAccount.get_Value(Changes.ECA64_ChargePercentage)).orElse(Env.ZERO);
							BigDecimal chargeAmount = getPercentageAmount(payment.getPayAmt(false), chargePercentage, precision);
							if(chargeAmount.abs().add(toleranceAmount).compareTo(difference.abs()) >= 0 && chargeAmount.abs().subtract(toleranceAmount).compareTo(difference.abs()) <= 0) {
								ibs.setChargeAmt(difference);
							} else {
								ibs.setChargeAmt(chargeAmount.negate());
							}
							ibs.setC_Charge_ID(chargeId);
						}
						if(productAccount.get_ValueAsBoolean(Changes.ECA64_IsApplyInterest)) {
							BigDecimal finalDifference = payment.getPayAmt(false).subtract(ibs.getStmtAmt()).subtract(ibs.getChargeAmt().abs());
							BigDecimal interestPercentage = Optional.ofNullable((BigDecimal) productAccount.get_Value(Changes.ECA64_InterestPercentage)).orElse(Env.ZERO);
							BigDecimal interestAmount = getPercentageAmount(payment.getPayAmt(false), interestPercentage, precision);
							if(interestAmount.abs().add(toleranceAmount).compareTo(finalDifference.abs()) >= 0 && interestAmount.abs().subtract(toleranceAmount).compareTo(finalDifference.abs()) <= 0) {
								if(finalDifference.signum() > 0) {
									finalDifference = finalDifference.negate();
								}
								ibs.setInterestAmt(finalDifference);
							} else {
								ibs.setInterestAmt(interestAmount.negate());
							}
						}
					}
				}
			}
//		});
		}
		return info;
	}
	
	private List<Integer> getAccountProducts(Properties context, int bankAccountId) {
		return new Query(context, Changes.TableName_ECA64_BankAccountProduct, "C_BankAccount_ID = ?", null)
				.setParameters(bankAccountId)
				.setOnlyActiveRecords(true)
				.getIDsAsList();
	}
	
	private BigDecimal getProductPaymentAmount(PO productAccount, BigDecimal statementAmount, int precision) {
		BigDecimal percentage = Env.ZERO;
		if(productAccount.get_ValueAsBoolean(Changes.ECA64_IsApplyCharge)) {
			percentage = Optional.ofNullable((BigDecimal) productAccount.get_Value(Changes.ECA64_ChargePercentage)).orElse(Env.ZERO);
		}
		if(productAccount.get_ValueAsBoolean(Changes.ECA64_IsApplyInterest)) {
			percentage = percentage.add(Optional.ofNullable((BigDecimal) productAccount.get_Value(Changes.ECA64_InterestPercentage)).orElse(Env.ZERO));
		}
		BigDecimal amount = statementAmount.add(getBasePercentageAmount(statementAmount, percentage));
		if(precision > 0) {
			amount = amount.setScale(precision, RoundingMode.HALF_UP);
		}
		return amount;
	}
	
	private BigDecimal getBasePercentageAmount(BigDecimal baseAmount, BigDecimal percentage) {
		BigDecimal divisor = Env.ONE.subtract(percentage.divide(Env.ONEHUNDRED, MathContext.DECIMAL128));
		if(divisor.signum() > 0) {
			return baseAmount.divide(divisor, MathContext.DECIMAL128).subtract(baseAmount);
		}
		return Env.ZERO;
	}
	
	private BigDecimal getPercentageAmount(BigDecimal baseAmount, BigDecimal percentage, int precision) {
		BigDecimal amount = baseAmount.multiply(percentage.divide(Env.ONEHUNDRED, MathContext.DECIMAL128));
		if(precision > 0) {
			amount = amount.setScale(precision, RoundingMode.HALF_UP);
		}
		return amount;
	}
}
