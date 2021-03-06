package com.github.sgov.server.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.sgov.server.environment.Generator;
import com.github.sgov.server.model.UserAccount;
import com.github.sgov.server.util.Vocabulary;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.metamodel.FieldSpecification;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DescriptorFactoryTest {

    private UserAccount modelObject;

    private FieldSpecification passwordFieldSpec;

    @BeforeEach
    void setUp() {
        this.modelObject = Generator.generateUserAccount();
        this.passwordFieldSpec = mock(FieldSpecification.class);
        when(passwordFieldSpec.getJavaField()).thenReturn(UserAccount.getPasswordField());
    }

    @Test
    void userDescriptorCreatesSimpleUserDescriptor() {
        final Descriptor result = DescriptorFactory.userManagementDescriptor(modelObject);
        assertEquals(URI.create(Vocabulary.s_c_uzivatel), result.getContext());
        assertEquals(URI.create(Vocabulary.s_c_uzivatel),
            result.getAttributeContext(passwordFieldSpec));
    }
}
