package com.acme.benchmark;

public class ImpactFacade {
    public Object[] exerciseAll(final String value) {
        return new Object[] {
            new BodyChangeUseCase().execute(value.length()),
            new RemovedMethodUseCase().execute(value),
            new DescriptorChangeUseCase().executeNewDescriptor(value),
            new DescriptorChangeUseCase().executeOldDescriptor(value),
            new RemovedFieldUseCase().execute(),
            new FieldDescriptorUseCase().executeNewDescriptor(),
            new FieldDescriptorUseCase().executeOldDescriptor(),
            new RemovedClassUseCase().execute(),
            new RecursiveCallUseCase().execute(value.length()),
            new BoundaryUseCase().exercise(),
            new AncestorRetentionUseCase().exercise(value.length())
        };
    }
}
