package com.gihub.quarkus.criteria.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.apache.deltaspike.data.impl.RepositoryExtension;
import org.apache.deltaspike.data.impl.tx.ThreadLocalEntityManagerHolder;
import org.apache.deltaspike.jpa.impl.entitymanager.DefaultEntityManagerHolder;

import javax.enterprise.inject.Vetoed;
import java.io.IOException;
import java.util.List;

public class QuarkusCriteriaProcessor {

    private static final List<String> BEANS_TO_VETO = List.of("org.apache.deltaspike.jpa.impl.entitymanager.DefaultEntityManagerHolder",
            "org.apache.deltaspike.data.impl.RepositoryExtension",
            "org.apache.deltaspike.data.impl.handler.QueryHandler", "org.apache.deltaspike.data.impl.meta.",
            "org.apache.deltaspike.core.impl.scope.", "org.apache.deltaspike.data.impl.tx.");

    @BuildStep
    void buildFeature(BuildProducer<FeatureBuildItem> feature) throws IOException {
        feature.produce(new FeatureBuildItem("quarkus-criteria"));
    }

    @BuildStep
    void removeBeans(BeanRegistrationPhaseBuildItem beanRegistrationPhase) {
        beanRegistrationPhase.getContext()
                .configure(RepositoryExtension.class).addQualifier(Vetoed.class).done();
        beanRegistrationPhase.getContext()
                .configure(DefaultEntityManagerHolder.class).addQualifier(Vetoed.class).done();
        beanRegistrationPhase.getContext()
                .configure(ThreadLocalEntityManagerHolder.class).addQualifier(Vetoed.class).done();
    }

    @BuildStep
    void buildCdiBeans(BuildProducer<AdditionalBeanBuildItem> additionalBean,
                       BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformerBuildItemBuildProducer) throws IOException {

        annotationsTransformerBuildItemBuildProducer.produce(new AnnotationsTransformerBuildItem(new AnnotationsTransformer() {
            public boolean appliesTo(org.jboss.jandex.AnnotationTarget.Kind kind) {
                return kind == org.jboss.jandex.AnnotationTarget.Kind.CLASS;
            }

            public void transform(AnnotationsTransformer.TransformationContext context) {
                BEANS_TO_VETO.
                        forEach(toVeto -> {
                            if (context.getTarget().asClass().name().toString().startsWith(toVeto)) {
                                context.transform().add(Vetoed.class).done();
                            }
                        });
            }
        }));
    }

}
