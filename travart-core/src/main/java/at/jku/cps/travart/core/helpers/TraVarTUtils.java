package at.jku.cps.travart.core.helpers;

import at.jku.cps.travart.core.common.IConfigurable;
import at.jku.cps.travart.core.transformation.DefaultModelTransformationProperties;
import de.vill.main.UVLModelFactory;
import de.vill.model.Attribute;
import de.vill.model.Feature;
import de.vill.model.Group;
import de.vill.model.Group.GroupType;
import de.vill.model.constraint.AndConstraint;
import de.vill.model.constraint.Constraint;
import de.vill.model.constraint.EquivalenceConstraint;
import de.vill.model.constraint.ImplicationConstraint;
import de.vill.model.constraint.LiteralConstraint;
import de.vill.model.constraint.NotConstraint;
import de.vill.model.constraint.OrConstraint;
import de.vill.model.constraint.ParenthesisConstraint;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.cps.travart.core.transformation.DefaultModelTransformationProperties.ABSTRACT_ATTRIBUTE;

public class TraVarTUtils {
    private static final UVLModelFactory factory = new UVLModelFactory();

    public static String[] splitString(final String toSplit, final String delimiter) {
        return Arrays.stream(toSplit.split(delimiter)).map(String::trim).filter(s -> !s.isEmpty() && !s.isBlank())
                .toArray(String[]::new);
    }

    /**
     * Creates a Set of names of the selected IConfigurable names.
     *
     * @param samples - the samples of the feature model.
     * @return A Set of name sets of the configuration samples.
     */
    public static Set<Set<String>> createConfigurationNameSet(final Set<Map<IConfigurable, Boolean>> samples) {
        final Set<Set<String>> configurations = new HashSet<>();
        for (final Map<IConfigurable, Boolean> sample : samples) {
            final Set<String> sampleNames = new HashSet<>();
            for (final Map.Entry<IConfigurable, Boolean> sampleEntry : sample.entrySet()) {
                if (sampleEntry.getValue()) {
                    sampleNames.add(sampleEntry.getKey().getName());
                }
            }
            configurations.add(sampleNames);
        }
        return configurations;
    }

    /**
     * This function recursively translates a propositional logic formula from the
     * data model used in the de.neominik.uvl library to the data model used in the
     * logicng library, to facilitate the later translation to pure variants
     * relations. Does not allow expression constraints.
     *
     * @param constraint the current node of the propositional formula
     * @param factory    The FormulaFactory required to create formulas for logicng
     * @return the logic formula parsed for the logicng library
     */
    public static Formula buildFormulaFromConstraint(final Constraint constraint, final FormulaFactory factory) {
        Formula term = null;
        if (constraint instanceof ImplicationConstraint) {
            term = factory.implication(buildFormulaFromConstraint(((ImplicationConstraint) constraint).getLeft(), factory),
                    buildFormulaFromConstraint(((ImplicationConstraint) constraint).getRight(), factory));
        } else if (constraint instanceof EquivalenceConstraint) {
            term = factory.equivalence(buildFormulaFromConstraint(((EquivalenceConstraint) constraint).getLeft(), factory),
                    buildFormulaFromConstraint(((EquivalenceConstraint) constraint).getRight(), factory));
        } else if (constraint instanceof AndConstraint) {
            term = factory.and(buildFormulaFromConstraint(((AndConstraint) constraint).getLeft(), factory),
                    buildFormulaFromConstraint(((AndConstraint) constraint).getRight(), factory));
        } else if (constraint instanceof OrConstraint) {
            term = factory.or(buildFormulaFromConstraint(((OrConstraint) constraint).getLeft(), factory),
                    buildFormulaFromConstraint(((OrConstraint) constraint).getRight(), factory));
        } else if (constraint instanceof NotConstraint) {
            term = factory.not(buildFormulaFromConstraint(((NotConstraint) constraint).getContent(), factory));
        } else if (constraint instanceof ParenthesisConstraint) {
            term = buildFormulaFromConstraint(((ParenthesisConstraint) constraint).getContent(), factory);
        } else if (constraint instanceof LiteralConstraint) {
            term = factory.literal(((LiteralConstraint) constraint).getLiteral(), true);
        }
        return term;
    }

    /**
     * Translates a Formula back to a constraint format.
     *
     * @param formula the formula in logicNG format
     * @return the same formula represented by UVLs Constraint hierarchy.
     */
    public static Constraint buildConstraintFromFormula(final Formula formula) {
        // replace negation sign for uvl parser to recognize it.
        return factory.parseConstraint(formula.toString().replace("~", "!"));
    }

    /**
     * Recursively counts all literals in a given constraint tree.
     *
     * @param constraint The constraint to count the literals from
     * @return the number of literals
     */
    public static int countLiterals(final Constraint constraint) {
        if (constraint instanceof LiteralConstraint) {
            return 1;
        }
        int i = 0;
        for (final Constraint subconst : constraint.getConstraintSubParts()) {
            i += countLiterals(subconst);
        }
        return i;
    }

    /**
     * Iterate over the {@code createConfigurationNameSet} configurations and find
     * the common feature names of the configurations
     *
     * @param samples - the samples of the feature model.
     * @return A Set of name sets of the configuration samples.
     */
    public static Set<String> getCommonConfigurationNameSet(final Set<Map<IConfigurable, Boolean>> samples) {
        final Set<String> commonNames = new HashSet<>();
        final Set<Set<String>> configurations = createConfigurationNameSet(samples);
        final Iterator<Set<String>> iterator = configurations.iterator();
        Set<String> element = iterator.next();
        iterator.remove();
        commonNames.addAll(element);
        while (iterator.hasNext()) {
            element = iterator.next();
            commonNames.retainAll(element);
        }
        return commonNames;
    }

    public static boolean isParentFeatureOf(final Feature child, final Feature parent) {
        if (child == null || parent == null) {
            return false;
        }
        Optional<Feature> iterate = getParent(child, child, null);
        final String parentName = parent.getFeatureName();
        while (iterate.isPresent()) {
            if (iterate.get().getFeatureName().equals(parentName)) {
                return true;
            }
            iterate = getParent(iterate.get(), iterate.get(), null);
        }

        return false;
    }

    public static boolean isEnumerationType(final Feature feature) {
        return feature.getChildren()
                .stream()
                .anyMatch(group -> (group.GROUPTYPE.equals(Group.GroupType.ALTERNATIVE)
                        || group.GROUPTYPE.equals(Group.GroupType.OR)
                        // todo: there was something multiple here, check if it works without it :D
                        // || group.GROUPTYPE.equals(Group.GroupType.GROUP_CARDINALITY)
                ));
    }

    public static Optional<Feature> getParent(final Feature feature, final Feature root, final Feature parent) {
        if (root.getChildren().isEmpty()) {
            return Optional.of(parent);
        }

        final List<Feature> children = getChildren(root);
        if (children.contains(feature)) {
            return Optional.of(root);
        }

        return children.stream().map(child -> getParent(feature, child, parent)).flatMap(Optional::stream).findFirst();
    }

    public static boolean isAbstract(final Feature feature) {
        return feature.getAttributes().get(ABSTRACT_ATTRIBUTE) != null && Boolean.parseBoolean(feature.getAttributes().get(ABSTRACT_ATTRIBUTE).getValue().toString());
    }

    public static Object getAttributeValue(final Feature feature, final String attributeName) {
        return feature.getAttributes().get(attributeName) == null ? null : feature.getAttributes().get(attributeName).getValue();
    }

    public static List<Feature> getChildren(final Feature feature) {
        final List<Feature> children = new ArrayList<>();
        feature.getChildren().forEach(group -> children.addAll(group.getFeatures()));

        return children;
    }

    public static Boolean checkGroupType(final Feature feature, final Group.GroupType groupType) {
        return feature.getParentGroup() == null ? Boolean.FALSE : feature.getParentGroup().GROUPTYPE.equals(groupType);
    }

    public static boolean isComplexConstraint(final Constraint constraint) {
        if (constraint instanceof LiteralConstraint) {
            return false;
        }
        boolean isComplex = false;
        for (final Constraint child : constraint.getConstraintSubParts()) {
            isComplex = isComplex || !(child instanceof LiteralConstraint);
        }
        return isComplex;
    }

    public static boolean isRequires(final Constraint constraint) {
        if (!(constraint instanceof OrConstraint) || constraint.getConstraintSubParts().size() != 2) {
            return false;
        }
        final OrConstraint or = (OrConstraint) constraint;
        final Constraint left = or.getLeft();
        final Constraint right = or.getRight();
        // Not(A) or B || A or Not(B) --> Both are implies constraints
        return isNegativeLiteral(left) && isPositiveLiteral(right)
                || isPositiveLiteral(left) && isNegativeLiteral(right);
    }

    public static Constraint getFirstNegativeLiteral(final Constraint constraint) {
        if (isNegativeLiteral(constraint)) {
            return constraint;
        }
        for (final Constraint child : constraint.getConstraintSubParts()) {
            if (isNegativeLiteral(child)) {
                return child;
            }
        }
        return null;
    }

    public static Constraint getFirstPositiveLiteral(final Constraint constraint) {
        if (isPositiveLiteral(constraint)) {
            return constraint;
        }
        for (final Constraint child : constraint.getConstraintSubParts()) {
            if (isPositiveLiteral(child)) {
                return child;
            }
        }
        return null;
    }

    public static boolean isExcludes(final Constraint constraint) {
        if (!(constraint instanceof OrConstraint) || constraint.getConstraintSubParts().size() != 2) {
            return false;
        }
        final OrConstraint or = (OrConstraint) constraint;
        final Constraint left = or.getLeft();
        final Constraint right = or.getRight();
        // Not(A) or Not(B) --> excludes
        // TODO: check
        return (isNegativeLiteral(left)) && (isNegativeLiteral(right));
    }

    public static boolean isNegativeLiteral(final Constraint constraint) {
        return (constraint instanceof NotConstraint)
                && (((NotConstraint) constraint).getContent() instanceof LiteralConstraint);
    }

    public static boolean isPositiveLiteral(final Constraint constraint) {
        return constraint instanceof LiteralConstraint;
    }

    public static int getMaxDepth(final Constraint constraint) {
        int count = 1;
        for (final Constraint child : constraint.getConstraintSubParts()) {
            final int childCount = getMaxDepth(child) + 1;
            if (childCount > count) {
                count = childCount;
            }
        }
        return count;
    }

    /**
     * if the given constraint can have a right sub-constraint, it returns
     * it.Otherwise null.
     *
     * @param constraint the constraint from which to get the right part of
     * @return the right sub-constraint
     */
    public static Constraint getRightConstraint(final Constraint constraint) {
        final List<Method> methods = Arrays.asList(constraint.getClass().getMethods());
        final Optional<Method> getRightMethod = methods.stream()
                .filter(m -> m.getName().equals("getRight"))
                .findAny();
        if (getRightMethod.isPresent()) {
            try {
                return (Constraint) getRightMethod.get().invoke(constraint);
            } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * if the given constraint can have a left sub-constraint, it returns it.
     * Otherwise null.
     *
     * @param constraint the constraint from which to get the left part of
     * @return the left sub-constraint
     */
    public static Constraint getLeftConstraint(final Constraint constraint) {
        final List<Method> methods = Arrays.asList(constraint.getClass().getMethods());
        final Optional<Method> getLeftMethod = methods.stream()
                .filter(m -> m.getName().equals("getLeft"))
                .findAny();
        if (getLeftMethod.isPresent()) {
            try {
                return (Constraint) getLeftMethod.get().invoke(constraint);
            } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Finds all features in a featuremap that do not have a parent are therefore
     * roots.
     *
     * @param featureMap featuremap containing all features of an UVL model
     * @return A list of features that don't have a parent
     */
    private static List<Feature> findRoots(final Map<String, Feature> featureMap) {
        // return all features with no parent
        return featureMap.values()
                .stream()
                .filter(f -> f.getParentFeature() == null)
                .collect(Collectors.toList());
    }

    /**
     * Gives back a valid feature tree for an UVL model. If more than one root are
     * contained in the feature map, they are united under one single virtual root.
     *
     * @param featureMap featuremap containing all features of an UVL model
     * @param rootName   Name of the artificial root
     * @return name of the root feature
     */
    public static String deriveFeatureModelRoot(final Map<String, Feature> featureMap, final String rootName) {
        final List<Feature> roots = findRoots(featureMap);
        if (roots.isEmpty()) {
            return null;
        }
        if (roots.size() > 1) {
            // artificial root - abstract and hidden
            final Feature artificialRoot = new Feature(rootName);
            artificialRoot.getAttributes().put(ABSTRACT_ATTRIBUTE, new Attribute<Boolean>(ABSTRACT_ATTRIBUTE, true));
            artificialRoot.getAttributes().put("hidden", new Attribute<Boolean>("hidden", true));
            // add property to identify the virtual root
            artificialRoot.getAttributes().put(
                    "ARTIFICIAL_MODEL_NAME",
                    new Attribute<String>(
                            "ARTIFICIAL_MODEL_NAME",
                            DefaultModelTransformationProperties.ARTIFICIAL_MODEL_NAME
                    )
            );
            final Group mandatoryGroup = new Group(GroupType.MANDATORY);
            mandatoryGroup.getFeatures().addAll(roots);
            artificialRoot.addChildren(mandatoryGroup);
            featureMap.put(rootName, artificialRoot);

            return rootName;
        }

        final Feature root = roots.get(0);
        return root.getFeatureName();
    }

    public static boolean isSingleFeatureRequires(final Constraint constraint) {
        if (constraint instanceof OrConstraint) {
            final OrConstraint orConstraint = (OrConstraint) constraint;
            return isNegativeLiteral(orConstraint.getLeft()) && isPositiveLiteral(orConstraint.getRight());
        } else if (constraint instanceof ImplicationConstraint) {
            final ImplicationConstraint implConstraint = (ImplicationConstraint) constraint;
            return isPositiveLiteral(implConstraint.getLeft()) && isPositiveLiteral(implConstraint.getRight());
        }
        return false;
    }

    public static boolean isSingleFeatureExcludes(final Constraint constraint) {
        if (constraint instanceof OrConstraint) {
            final OrConstraint orConstraint = (OrConstraint) constraint;
            return isNegativeLiteral(orConstraint.getLeft()) && isNegativeLiteral(orConstraint.getRight());
        } else if (constraint instanceof ImplicationConstraint) {
            final ImplicationConstraint implConstraint = (ImplicationConstraint) constraint;
            return isPositiveLiteral(implConstraint.getLeft()) && isNegativeLiteral(implConstraint.getRight());
        }
        return false;
    }

    public static boolean hasNegativeLiteral(final Constraint constraint) {
        return countNegativeLiterals(constraint) > 0;
    }

    public static int countNegativeLiterals(final Constraint constraint) {
        if (constraint instanceof NotConstraint && ((NotConstraint) constraint).getContent() instanceof LiteralConstraint) {
            return 1;
        }
        if (constraint instanceof ParenthesisConstraint) {
            ((ParenthesisConstraint) constraint).getContent();
        }
        int i = 0;
        for (final Constraint subConst : constraint.getConstraintSubParts()) {
            i += countPositiveLiterals(subConst);
        }
        return i;
    }

    public static boolean hasPositiveLiteral(final Constraint constraint) {
        return countPositiveLiterals(constraint) > 0;
    }

    public static int countPositiveLiterals(final Constraint constraint) {
        if (constraint instanceof NotConstraint && ((NotConstraint) constraint).getContent() instanceof LiteralConstraint) {
            return 0;
        }
        if (constraint instanceof ParenthesisConstraint) {
            ((ParenthesisConstraint) constraint).getContent();
        }
        int i = 0;
        for (final Constraint subConst : constraint.getConstraintSubParts()) {
            i += countPositiveLiterals(subConst);
        }
        return i;
    }

    public static Set<Constraint> getNegativeLiterals(final Constraint constraint) {
        final Set<Constraint> literals = new HashSet<>();
        if (constraint instanceof NotConstraint && ((NotConstraint) constraint).getContent() instanceof LiteralConstraint) {
            literals.add(((NotConstraint) constraint).getContent());
            return literals;
        }
        if (constraint instanceof ParenthesisConstraint) {
            ((ParenthesisConstraint) constraint).getContent();
        }
        for (final Constraint subConst : constraint.getConstraintSubParts()) {
            literals.addAll(getNegativeLiterals(subConst));
        }

        return literals;
    }

    public static Set<Constraint> getLiterals(final Constraint constraint) {
        final Set<Constraint> literals = new HashSet<>();
        if (constraint instanceof LiteralConstraint) {
            literals.add(constraint);
            return literals;
        } else {
            if (constraint instanceof ParenthesisConstraint) {
                ((ParenthesisConstraint) constraint).getContent();
            }
            for (final Constraint subConst : constraint.getConstraintSubParts()) {
                literals.addAll(getLiterals(subConst));
            }
        }
        return literals;
    }

    public static boolean isLiteral(final Constraint constraint) {
        return constraint instanceof LiteralConstraint || constraint instanceof NotConstraint
                && ((NotConstraint) constraint).getContent() instanceof LiteralConstraint;
    }

    public static Set<Constraint> getPositiveLiterals(final Constraint constraint) {
        final Set<Constraint> literals = new HashSet<>();
        if (constraint instanceof LiteralConstraint) {
            literals.add(constraint);
            return literals;
        }
        if (constraint instanceof ParenthesisConstraint) {
            ((ParenthesisConstraint) constraint).getContent();
        }
        for (final Constraint subConst : constraint.getConstraintSubParts()) {
            literals.addAll(getNegativeLiterals(subConst));
        }

        return literals;
    }
}