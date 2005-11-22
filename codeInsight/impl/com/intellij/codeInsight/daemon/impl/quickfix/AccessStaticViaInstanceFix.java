package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class AccessStaticViaInstanceFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix");

  private final PsiReferenceExpression myExpression;
  private final PsiMember myMember;
  private final JavaResolveResult myResult;

  public AccessStaticViaInstanceFix(PsiReferenceExpression expression, JavaResolveResult result) {
    myExpression = expression;
    myMember = (PsiMember)result.getElement();
    myResult = result;
  }

  public String getName() {
    PsiClass aClass = myMember.getContainingClass();
    return QuickFixBundle.message("access.static.via.class.reference.text",
                                  HighlightMessageUtil.getSymbolName(myMember, myResult.getSubstitutor()),
                                  HighlightUtil.formatClass(aClass),
                                  HighlightUtil.formatClass(aClass,false));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("access.static.via.class.reference.family");
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    if (!CodeInsightUtil.prepareFileForWrite(myExpression.getContainingFile())) return;
    try {
      PsiExpression qualifierExpression = myExpression.getQualifierExpression();
      PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
      if (qualifierExpression instanceof PsiThisExpression &&
          ((PsiThisExpression) qualifierExpression).getQualifier() == null) {
        // this.field -> field
        qualifierExpression.delete();
        if (myExpression.resolve() != myMember) {
          PsiReferenceExpression expr = (PsiReferenceExpression) factory.createExpressionFromText("A.foo", myExpression);
          expr.getQualifierExpression().replace(factory.createReferenceExpression(myMember.getContainingClass()));
          expr.getReferenceNameElement().replace(myExpression);
          myExpression.replace(expr);
        }
      }
      else {
        qualifierExpression.replace(factory.createReferenceExpression(myMember.getContainingClass()));
      }

      UndoManager.getInstance(project).markDocumentForUndo(myMember.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
