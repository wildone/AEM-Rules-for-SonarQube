package com.cognifide.aemrules.extensions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.FieldUtils2;
import org.sonar.check.Cardinality;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import org.sonar.plugins.java.api.JavaCheck;

public class RulesLoader {

	private static final Logger LOG = LoggerFactory.getLogger(RulesLoader.class);

	private static final Function<Class<?>, RuleParamType> TYPE_FOR_CLASS = Functions.forMap(
			ImmutableMap.<Class<?>, RuleParamType>builder()
					.put(Integer.class, RuleParamType.INTEGER)
					.put(int.class, RuleParamType.INTEGER)
					.put(Float.class, RuleParamType.FLOAT)
					.put(float.class, RuleParamType.FLOAT)
					.put(Boolean.class, RuleParamType.BOOLEAN)
					.put(boolean.class, RuleParamType.BOOLEAN)
					.build(),
			RuleParamType.STRING
	);

	public void load(RulesDefinition.NewExtendedRepository repo, List<Class<? extends JavaCheck>> annotatedClasses) {
		for (Class<? extends JavaCheck> annotatedClass : annotatedClasses) {
			loadRule(repo, annotatedClass);
		}
	}

	@CheckForNull
	RulesDefinition.NewRule loadRule(RulesDefinition.NewExtendedRepository repo, Class<? extends JavaCheck> clazz) {
		org.sonar.check.Rule ruleAnnotation = AnnotationUtils.getAnnotation(clazz, org.sonar.check.Rule.class);
		if (ruleAnnotation != null) {
			return loadRule(repo, clazz, ruleAnnotation);
		} else {
			LOG.warn("The class " + clazz.getCanonicalName() + " should be annotated with " + org.sonar.check.Rule.class);
			return null;
		}
	}

	private RulesDefinition.NewRule loadRule(RulesDefinition.NewExtendedRepository repo, Class<? extends JavaCheck> clazz, org.sonar.check.Rule ruleAnnotation) {
		String ruleKey = StringUtils.defaultIfEmpty(ruleAnnotation.key(), clazz.getCanonicalName());
		String ruleName = StringUtils.defaultIfEmpty(ruleAnnotation.name(), null);
		String description = StringUtils.defaultIfEmpty(getDescriptionFromResources(ruleKey), "No description yet.");

		RulesDefinition.NewRule rule = repo.createRule(ruleKey);
		rule.setName(ruleName).setMarkdownDescription(description);
		rule.setSeverity(ruleAnnotation.priority().name());
		rule.setTemplate(ruleAnnotation.cardinality() == Cardinality.MULTIPLE);
		rule.setStatus(RuleStatus.valueOf(ruleAnnotation.status()));
		rule.setTags(ruleAnnotation.tags());

		List<Field> fields = FieldUtils2.getFields(clazz, true);
		for (Field field : fields) {
			loadParameters(rule, field);
		}

		return rule;
	}

	protected String getDescriptionFromResources(String ruleKey) {
		String result = null;
		try {
			String path = String.format("/rules/%s.md", ruleKey);
			URL url = Resources.getResource(RulesLoader.class, path);
			result = Resources.toString(url, Charsets.UTF_8);
		} catch (IOException | IllegalArgumentException e) {
			LOG.error("Cannot read resource file with rule description.", e);
		}
		return result;
	}

	private void loadParameters(RulesDefinition.NewRule rule, Field field) {
		org.sonar.check.RuleProperty propertyAnnotation = field.getAnnotation(org.sonar.check.RuleProperty.class);
		if (propertyAnnotation != null) {
			String fieldKey = StringUtils.defaultIfEmpty(propertyAnnotation.key(), field.getName());
			RulesDefinition.NewParam param = rule.createParam(fieldKey)
					.setDescription(propertyAnnotation.description())
					.setDefaultValue(propertyAnnotation.defaultValue());

			if (!StringUtils.isBlank(propertyAnnotation.type())) {
				try {
					param.setType(RuleParamType.parse(propertyAnnotation.type().trim()));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("Invalid property type [" + propertyAnnotation.type() + "]", e);
				}
			} else {
				param.setType(guessType(field.getType()));
			}
		}
	}

	@VisibleForTesting
	static RuleParamType guessType(Class<?> type) {
		return TYPE_FOR_CLASS.apply(type);
	}
}
