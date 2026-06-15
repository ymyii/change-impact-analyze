package io.github.changeimpact.analyze.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeImpactAnalyzeCliTest {

    @Test
    void helpOptionReturnsZero() {
        final int code =
                new CommandLine(new ChangeImpactAnalyzeCli())
                        .execute("--help");
        assertThat(code).isZero();
    }

    @Test
    void noArgsReturnsZero() {
        final int code =
                new CommandLine(new ChangeImpactAnalyzeCli()).execute();
        assertThat(code).isZero();
    }
}
