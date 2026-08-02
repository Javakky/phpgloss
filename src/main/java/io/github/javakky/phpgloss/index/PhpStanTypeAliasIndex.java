package io.github.javakky.phpgloss.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import io.github.javakky.phpgloss.model.PhpStanTypeAliasDefinition;
import io.github.javakky.phpgloss.parser.PhpFileModel;
import io.github.javakky.phpgloss.parser.PhpFileModelParser;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PhpStanTypeAliasIndex extends FileBasedIndexExtension<String, PhpStanTypeAliasIndexValue> {
    public static final ID<String, PhpStanTypeAliasIndexValue> NAME = ID.create("io.github.javakky.phpgloss.alias");
    public static final int VERSION = 1;

    private static final PhpFileModelParser FILE_PARSER = new PhpFileModelParser();
    private static final DataExternalizer<PhpStanTypeAliasIndexValue> VALUE_EXTERNALIZER = new DataExternalizer<>() {
        @Override
        public void save(@NotNull DataOutput out, PhpStanTypeAliasIndexValue value) throws IOException {
            out.writeUTF(value.classFqcn());
            out.writeUTF(value.aliasName());
            DataInputOutputUtil.writeINT(out, value.aliasNameOffset());
        }

        @Override
        public PhpStanTypeAliasIndexValue read(@NotNull DataInput in) throws IOException {
            return new PhpStanTypeAliasIndexValue(in.readUTF(), in.readUTF(), DataInputOutputUtil.readINT(in));
        }
    };

    @Override
    public @NotNull ID<String, PhpStanTypeAliasIndexValue> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, PhpStanTypeAliasIndexValue, FileContent> getIndexer() {
        return inputData -> {
            Map<String, PhpStanTypeAliasIndexValue> result = new LinkedHashMap<>();
            PhpFileModel file = FILE_PARSER.parse(inputData.getContentAsText().toString());
            for (PhpFileModel.ClassLikeDocBlock block : file.classLikeDocBlocks()) {
                for (PhpStanTypeAliasDefinition alias : block.aliases()) {
                    String key = key(block.fqcn(), alias.aliasName());
                    result.put(key, new PhpStanTypeAliasIndexValue(block.fqcn(), alias.aliasName(), alias.aliasNameRange().startOffset()));
                }
            }
            return result;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<PhpStanTypeAliasIndexValue> getValueExternalizer() {
        return VALUE_EXTERNALIZER;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return this::isPhpFile;
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    public static String key(String fqcn, String aliasName) {
        return fqcn + "#" + aliasName;
    }

    private boolean isPhpFile(VirtualFile file) {
        return "php".equalsIgnoreCase(file.getExtension());
    }
}

