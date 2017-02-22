/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.shader;

import com.jme3.renderer.Renderer;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.IntMap;
import com.jme3.util.IntMap.Entry;
import com.jme3.util.ListMap;
import com.jme3.util.NativeObject;
import java.util.ArrayList;
import java.util.Collection;

public final class Shader extends NativeObject {
    
    /**
     * A list of all shader sources currently attached.
     */
    private final ArrayList<ShaderSource> shaderSourceList;

    /**
     * Maps uniform name to the uniform variable.
     */
    private final ListMap<String, Uniform> uniforms;
    
    /**
     * Uniforms bound to {@link UniformBinding}s.
     * 
     * Managed by the {@link UniformBindingManager}.
     */
    private final ArrayList<Uniform> boundUniforms;

    /**
     * Maps attribute name to the location of the attribute in the shader.
     */
    private final IntMap<Attribute> attribs;

    /**
     * Type of shader. The shader will control the pipeline of it's type.
     */
    public static enum ShaderType {

        /**
         * Control fragment rasterization. (e.g color of pixel).
         */
        Fragment("frag"),
        /**
         * Control vertex processing. (e.g transform of model to clip space)
         */
        Vertex("vert"),
        /**
         * Control geometry assembly. (e.g compile a triangle list from input
         * data)
         */
        Geometry("geom"),
        /**
         * Controls tesselation factor (e.g how often a input patch should be
         * subdivided)
         */
        TessellationControl("tsctrl"),
        /**
         * Controls tesselation transform (e.g similar to the vertex shader, but
         * required to mix inputs manual)
         */
        TessellationEvaluation("tseval");

        private String extension;
        
        public String getExtension() {
            return extension;
        }
        
        private ShaderType(String extension) {
            this.extension = extension;
        }
    }

    /**
     * Shader source describes a shader object in OpenGL. Each shader source
     * is assigned a certain pipeline which it controls (described by it's type).
     */
    public static class ShaderSource extends NativeObject {

        ShaderType sourceType;
        String language;
        String name;
        String source;
        String defines;

        public ShaderSource(ShaderType type){
            super();
            this.sourceType = type;
            if (type == null) {
                throw new IllegalArgumentException("The shader type must be specified");
            }
        }
        
        protected ShaderSource(ShaderSource ss){
            super(ss.id);
            // No data needs to be copied.
            // (This is a destructable clone)
        }

        public ShaderSource(){
            super();
        }

        public void setName(String name){
            this.name = name;
        }

        public String getName(){
            return name;
        }

        public ShaderType getType() {
            return sourceType;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            if (language == null) {
                throw new IllegalArgumentException("Shader language cannot be null");
            }
            this.language = language;
            setUpdateNeeded();
        }

        public void setSource(String source){
            if (source == null) {
                throw new IllegalArgumentException("Shader source cannot be null");
            }
            this.source = source;
            setUpdateNeeded();
        }

        public void setDefines(String defines){
            if (defines == null) {
                throw new IllegalArgumentException("Shader defines cannot be null");
            }
            this.defines = defines;
            setUpdateNeeded();
        }

        public String getSource(){
            return source;
        }

        public String getDefines(){
            return defines;
        }
        
        @Override
        public long getUniqueId() {
            return ((long)OBJTYPE_SHADERSOURCE << 32) | ((long)id);
        }
        
        @Override
        public String toString(){
            String nameTxt = "";
            if (name != null)
                nameTxt = "name="+name+", ";
            if (defines != null)
                nameTxt += "defines, ";
            

            return getClass().getSimpleName() + "["+nameTxt+"type="
                                              + sourceType.name()+", language=" + language + "]";
        }

        public void resetObject(){
            id = -1;
            setUpdateNeeded();
        }

        public void deleteObject(Object rendererObject){
            ((Renderer)rendererObject).deleteShaderSource(ShaderSource.this);
        }

        public NativeObject createDestructableClone(){
            return new ShaderSource(ShaderSource.this);
        }
    }

    /**
     * Creates a new shader, {@link #initialize() } must be called
     * after this constructor for the shader to be usable.
     */
    public Shader(){
        super();
        shaderSourceList = new ArrayList<ShaderSource>();
        uniforms = new ListMap<String, Uniform>();
        attribs = new IntMap<Attribute>();
        boundUniforms = new ArrayList<Uniform>();
    }

    /**
     * Do not use this constructor. Used for destructable clones only.
     */
    protected Shader(Shader s){
        super(s.id);
        
        // Shader sources cannot be shared, therefore they must
        // be destroyed together with the parent shader.
        shaderSourceList = new ArrayList<ShaderSource>();
        for (ShaderSource source : s.shaderSourceList){
            shaderSourceList.add( (ShaderSource)source.createDestructableClone() );
        }
        
        uniforms = null;
        boundUniforms = null;
        attribs = null;
    }

    /**
     * Adds source code to a certain pipeline.
     *
     * @param type The pipeline to control
     * @param source The shader source code (in GLSL).
     * @param defines Preprocessor defines (placed at the beginning of the shader)
     * @param language The shader source language, currently accepted is GLSL###
     * where ### is the version, e.g. GLSL100 = GLSL 1.0, GLSL330 = GLSL 3.3, etc.
     */
    public void addSource(ShaderType type, String name, String source, String defines, String language){
        ShaderSource shaderSource = new ShaderSource(type);
        shaderSource.setSource(source);
        shaderSource.setName(name);
        shaderSource.setLanguage(language);
        if (defines != null) {
            shaderSource.setDefines(defines);
        }
        shaderSourceList.add(shaderSource);
        setUpdateNeeded();
    }

    public void addUniformBinding(UniformBinding binding){
        String uniformName = "g_" + binding.name();
        Uniform uniform = uniforms.get(uniformName);
        if (uniform == null) {
            uniform = new Uniform();
            uniform.name = uniformName;
            uniform.binding = binding;
            uniforms.put(uniformName, uniform);
            boundUniforms.add(uniform);
        }
    }
    
    public Uniform getUniform(String name){
        assert name.startsWith("m_") || name.startsWith("g_");
        Uniform uniform = uniforms.get(name);
        if (uniform == null){
            uniform = new Uniform();
            uniform.name = name;
            uniforms.put(name, uniform);
        }
        return uniform;
    }

    public void removeUniform(String name){
        uniforms.remove(name);
    }

    public Attribute getAttribute(VertexBuffer.Type attribType){
        int ordinal = attribType.ordinal();
        Attribute attrib = attribs.get(ordinal);
        if (attrib == null){
            attrib = new Attribute();
            attrib.name = attribType.name();
            attribs.put(ordinal, attrib);
        }
        return attrib;
    }

    public ListMap<String, Uniform> getUniformMap(){
        return uniforms;
    }
    
    public ArrayList<Uniform> getBoundUniforms() {
        return boundUniforms;
    }

    public Collection<ShaderSource> getSources(){
        return shaderSourceList;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + 
                "[numSources=" + shaderSourceList.size() +
                ", numUniforms=" + uniforms.size() +
                ", shaderSources=" + getSources() + "]";
    }

    /**
     * Removes the "set-by-current-material" flag from all uniforms.
     * When a uniform is modified after this call, the flag shall
     * become "set-by-current-material". 
     * A call to {@link #resetUniformsNotSetByCurrent() } will reset
     * all uniforms that do not have the "set-by-current-material" flag
     * to their default value (usually all zeroes or false).
     */
    public void clearUniformsSetByCurrentFlag() {
        int size = uniforms.size();
        for (int i = 0; i < size; i++) {
            Uniform u = uniforms.getValue(i);
            u.clearSetByCurrentMaterial();
        }
    }

    /**
     * Resets all uniforms that do not have the "set-by-current-material" flag
     * to their default value (usually all zeroes or false).
     * When a uniform is modified, that flag is set, to remove the flag,
     * use {@link #clearUniformsSetByCurrent() }.
     */
    public void resetUniformsNotSetByCurrent() {
        int size = uniforms.size();
        for (int i = 0; i < size; i++) {
            Uniform u = uniforms.getValue(i);
            if (!u.isSetByCurrentMaterial()) {
                u.clearValue();
            }
        }
    }

    /**
     * Usually called when the shader itself changes or during any
     * time when the variable locations need to be refreshed.
     */
    public void resetLocations() {
        if (uniforms != null) {
            // NOTE: Shader sources will be reset seperately from the shader itself.
            for (Uniform uniform : uniforms.values()) {
                uniform.reset(); // fixes issue with re-initialization
            }
        }
        if (attribs != null) {
            for (Entry<Attribute> entry : attribs) {
                entry.getValue().location = ShaderVariable.LOC_UNKNOWN;
            }
        }
    }

    @Override
    public void setUpdateNeeded(){
        super.setUpdateNeeded();
        resetLocations();
    }

    /**
     * Called by the object manager to reset all object IDs. This causes
     * the shader to be reuploaded to the GPU incase the display was restarted.
     */
    @Override
    public void resetObject() {
        this.id = -1;
        for (ShaderSource source : shaderSourceList){
            source.resetObject();
        }
        setUpdateNeeded();
    }

    @Override
    public void deleteObject(Object rendererObject) {
        ((Renderer)rendererObject).deleteShader(this);
    }

    public NativeObject createDestructableClone(){
        return new Shader(this);
    }

    @Override
    public long getUniqueId() {
        return ((long)OBJTYPE_SHADER << 32) | ((long)id);
    }

    /*********************************************************************\
     |* Shaders                                                           *|
     \*********************************************************************/
    protected void updateUniformLocation(Shader shader, Uniform uniform, GL gl) {
        int loc = gl.glGetUniformLocation(shader.getId(), uniform.getName());
        if (loc < 0) {
            uniform.setLocation(-1);
            // uniform is not declared in shader
            logger.log(Level.FINE, "Uniform {0} is not declared in shader {1}.", new Object[]{uniform.getName(), shader.getSources()});
        } else {
            uniform.setLocation(loc);
        }
    }

    protected void bindProgram(ShaderParameters shaderParameters) {
        int shaderId = shaderParameters.shader.getId();
        if (shaderParameters.context.boundShaderProgram != shaderId) {
            shaderParameters.gl.glUseProgram(shaderId);
            shaderParameters.statistics.onShaderUse(shader, true);
            shaderParameters.context.boundShader = shader;
            shaderParameters.context.boundShaderProgram = shaderId;
        } else {
            shaderParameters.statistics.onShaderUse(shader, false);
        }
    }

    protected void updateUniform(ShaderParameters shaderParameters) {
        int shaderId = shader.getId();

        assert uniform.getName() != null;
        assert shader.getId() > 0;

        bindProgram(shader, gl, context, statistics);

        int loc = uniform.getLocation();
        if (loc == -1) {
            return;
        }

        if (loc == -2) {
            // get uniform location
            updateUniformLocation(shader, uniform, gl);
            if (uniform.getLocation() == -1) {
                // not declared, ignore
                uniform.clearUpdateNeeded();
                return;
            }
            loc = uniform.getLocation();
        }

        if (uniform.getVarType() == null) {
            return; // value not set yet..
        }
        statistics.onUniformSet();

        uniform.clearUpdateNeeded();
        FloatBuffer fb;
        IntBuffer ib;
        switch (uniform.getVarType()) {
        case Float:
            Float f = (Float) uniform.getValue();
            gl.glUniform1f(loc, f.floatValue());
            break;
        case Vector2:
            Vector2f v2 = (Vector2f) uniform.getValue();
            gl.glUniform2f(loc, v2.getX(), v2.getY());
            break;
        case Vector3:
            Vector3f v3 = (Vector3f) uniform.getValue();
            gl.glUniform3f(loc, v3.getX(), v3.getY(), v3.getZ());
            break;
        case Vector4:
            Object val = uniform.getValue();
            if (val instanceof ColorRGBA) {
                ColorRGBA c = (ColorRGBA) val;
                gl.glUniform4f(loc, c.r, c.g, c.b, c.a);
            } else if (val instanceof Vector4f) {
                Vector4f c = (Vector4f) val;
                gl.glUniform4f(loc, c.x, c.y, c.z, c.w);
            } else {
                Quaternion c = (Quaternion) uniform.getValue();
                gl.glUniform4f(loc, c.getX(), c.getY(), c.getZ(), c.getW());
            }
            break;
        case Boolean:
            Boolean b = (Boolean) uniform.getValue();
            gl.glUniform1i(loc, b.booleanValue() ? GL.GL_TRUE : GL.GL_FALSE);
            break;
        case Matrix3:
            fb = uniform.getMultiData();
            assert fb.remaining() == 9;
            gl.glUniformMatrix3(loc, false, fb);
            break;
        case Matrix4:
            fb = uniform.getMultiData();
            assert fb.remaining() == 16;
            gl.glUniformMatrix4(loc, false, fb);
            break;
        case IntArray:
            ib = (IntBuffer) uniform.getValue();
            gl.glUniform1(loc, ib);
            break;
        case FloatArray:
            fb = uniform.getMultiData();
            gl.glUniform1(loc, fb);
            break;
        case Vector2Array:
            fb = uniform.getMultiData();
            gl.glUniform2(loc, fb);
            break;
        case Vector3Array:
            fb = uniform.getMultiData();
            gl.glUniform3(loc, fb);
            break;
        case Vector4Array:
            fb = uniform.getMultiData();
            gl.glUniform4(loc, fb);
            break;
        case Matrix4Array:
            fb = uniform.getMultiData();
            gl.glUniformMatrix4(loc, false, fb);
            break;
        case Int:
            Integer i = (Integer) uniform.getValue();
            gl.glUniform1i(loc, i.intValue());
            break;
        default:
            throw new UnsupportedOperationException("Unsupported uniform type: " + uniform.getVarType());
        }
    }

    protected void updateShaderUniforms(ShaderParameters shaderParameters.) {
        ListMap<String, Uniform> uniforms = shader.getUniformMap();
        for (int i = 0; i < uniforms.size(); i++) {
            Uniform uniform = uniforms.getValue(i);
            if (uniform.isUpdateNeeded()) {
                updateUniform(shaderParameters.shader, uniform, shaderParameters.gl, shaderParameters.context, shaderParameters.statistics);
            }
        }
    }

    protected void resetUniformLocations(Shader shader) {
        ListMap<String, Uniform> uniforms = shader.getUniformMap();
        for (int i = 0; i < uniforms.size(); i++) {
            Uniform uniform = uniforms.getValue(i);
            uniform.reset(); // e.g check location again
        }
    }

    public int convertShaderType(ShaderType type) {
        switch (type) {
        case Fragment:
            return GL.GL_FRAGMENT_SHADER;
        case Vertex:
            return GL.GL_VERTEX_SHADER;
        case Geometry:
            return GL3.GL_GEOMETRY_SHADER;
        case TessellationControl:
            return GL4.GL_TESS_CONTROL_SHADER;
        case TessellationEvaluation:
            return GL4.GL_TESS_EVALUATION_SHADER;
        default:
            throw new UnsupportedOperationException("Unrecognized shader type.");
        }
    }

    public void updateShaderSourceData(ShaderParameters shaderParameters) {
        int id = source.getId();
        if (id == -1) {
            // Create id
            id = gl.glCreateShader(convertShaderType(source.getType()));
            if (id <= 0) {
                throw new RendererException("Invalid ID received when trying to create shader.");
            }

            source.setId(id);
        } else {
            throw new RendererException("Cannot recompile shader source");
        }

        boolean gles2 = caps.contains(Caps.OpenGLES20);
        String language = source.getLanguage();

        if (gles2 && !language.equals("GLSL100")) {
            throw new RendererException("This shader cannot run in OpenGL ES 2. "
                    + "Only GLSL 1.00 shaders are supported.");
        }

        // Upload shader source.
        // Merge the defines and source code.
        stringBuf.setLength(0);
        if (language.startsWith("GLSL")) {
            int version = Integer.parseInt(language.substring(4));
            if (version > 100) {
                stringBuf.append("#version ");
                stringBuf.append(language.substring(4));
                if (version >= 150) {
                    stringBuf.append(" core");
                }
                stringBuf.append("\n");
            } else {
                if (gles2) {
                    // request GLSL ES (1.00) when compiling under GLES2.
                    stringBuf.append("#version 100\n");

                    if (source.getType() == ShaderType.Fragment) {
                        // GLES2 requires precision qualifier.
                        stringBuf.append("precision mediump float;\n");
                    }
                } else {
                    // version 100 does not exist in desktop GLSL.
                    // put version 110 in that case to enable strict checking
                    // (Only enabled for desktop GL)
                    stringBuf.append("#version 110\n");
                }
            }
        }

        if (linearizeSrgbImages) {
            stringBuf.append("#define SRGB 1\n");
        }
        stringBuf.append("#define ").append(source.getType().name().toUpperCase()).append("_SHADER 1\n");

        stringBuf.append(source.getDefines());
        stringBuf.append(source.getSource());

        intBuf1.clear();
        intBuf1.put(0, stringBuf.length());
        gl.glShaderSource(id, new String[]{ stringBuf.toString() }, intBuf1);
        gl.glCompileShader(id);

        gl.glGetShader(id, GL.GL_COMPILE_STATUS, intBuf1);

        boolean compiledOK = intBuf1.get(0) == GL.GL_TRUE;
        String infoLog = null;

        if (VALIDATE_SHADER || !compiledOK) {
            // even if compile succeeded, check
            // log for warnings
            gl.glGetShader(id, GL.GL_INFO_LOG_LENGTH, intBuf1);
            int length = intBuf1.get(0);
            if (length > 3) {
                // get infos
                infoLog = gl.glGetShaderInfoLog(id, length);
            }
        }

        if (compiledOK) {
            if (infoLog != null) {
                logger.log(Level.WARNING, "{0} compiled successfully, compiler warnings: \n{1}",
                        new Object[]{source.getName(), infoLog});
            } else {
                logger.log(Level.FINE, "{0} compiled successfully.", source.getName());
            }
            source.clearUpdateNeeded();
        } else {
            if (infoLog != null) {
                throw new RendererException("compile error in: " + source + "\n" + infoLog);
            } else {
                throw new RendererException("compile error in: " + source + "\nerror: <not provided>");
            }
        }
    }

    public void updateShaderData(ShaderParameters shaderParameters) {
        int id = shader.getId();
        boolean needRegister = false;
        if (id == -1) {
            // create program
            id = gl.glCreateProgram();
            if (id == 0) {
                throw new RendererException("Invalid ID (" + id + ") received when trying to create shader program.");
            }

            shader.setId(id);
            needRegister = true;
        }

        // If using GLSL 1.5, we bind the outputs for the user
        // For versions 3.3 and up, user should use layout qualifiers instead.
        boolean bindFragDataRequired = false;

        for (ShaderSource source : shader.getSources()) {
            if (source.isUpdateNeeded()) {
                updateShaderSourceData(source, gl, caps, stringBuf, linearizeSrgbImages, intBuf1);
            }
            if (source.getType() == ShaderType.Fragment
                    && source.getLanguage().equals("GLSL150")) {
                bindFragDataRequired = true;
            }
            gl.glAttachShader(id, source.getId());
        }

        if (bindFragDataRequired) {
            // Check if GLSL version is 1.5 for shader
            gl3.glBindFragDataLocation(id, 0, "outFragColor");
            // For MRT
            for (int i = 0; i < limits.get(Limits.FrameBufferMrtAttachments); i++) {
                gl3.glBindFragDataLocation(id, i, "outFragData[" + i + "]");
            }
        }

        // Link shaders to program
        gl.glLinkProgram(id);

        // Check link status
        gl.glGetProgram(id, GL.GL_LINK_STATUS, intBuf1);
        boolean linkOK = intBuf1.get(0) == GL.GL_TRUE;
        String infoLog = null;

        if (VALIDATE_SHADER || !linkOK) {
            gl.glGetProgram(id, GL.GL_INFO_LOG_LENGTH, intBuf1);
            int length = intBuf1.get(0);
            if (length > 3) {
                // get infos
                infoLog = gl.glGetProgramInfoLog(id, length);
            }
        }

        if (linkOK) {
            if (infoLog != null) {
                logger.log(Level.WARNING, "Shader linked successfully. Linker warnings: \n{0}", infoLog);
            } else {
                logger.fine("Shader linked successfully.");
            }
            shader.clearUpdateNeeded();
            if (needRegister) {
                // Register shader for clean up if it was created in this method.
                objManager.registerObject(shader);
                statistics.onNewShader();
            } else {
                // OpenGL spec: uniform locations may change after re-link
                resetUniformLocations(shader);
            }
        } else {
            if (infoLog != null) {
                throw new RendererException("Shader failed to link, shader:" + shader + "\n" + infoLog);
            } else {
                throw new RendererException("Shader failed to link, shader:" + shader + "\ninfo: <not provided>");
            }
        }
    }

    public void setShader(ShaderParameters shaderParameters) {
        if (shader == null) {
            throw new IllegalArgumentException("Shader cannot be null");
        } else {
            if (shader.isUpdateNeeded()) {
                updateShaderData(shader, source, gl, caps, stringBuf, linearizeSrgbImages, intBuf1, gl3, objManager, statistics);
            }

            // NOTE: might want to check if any of the
            // sources need an update?

            assert shader.getId() > 0;

            updateShaderUniforms(shader);
            bindProgram(shader, gl, context, statistics);
        }
    }

    public void deleteShaderSource(ShaderSource source, GL gl) {
        if (source.getId() < 0) {
            logger.warning("Shader source is not uploaded to GPU, cannot delete.");
            return;
        }
        source.clearUpdateNeeded();
        gl.glDeleteShader(source.getId());
        source.resetObject();
    }

    public void deleteShader(ShaderParameters shaderParameters) {
        if (shaderParameters.shader.getId() == -1) {
            logger.warning("Shader is not uploaded to GPU, cannot delete.");
            return;
        }

        for (ShaderSource source : shaderParameters.shader.getSources()) {
            if (source.getId() != -1) {
                shaderParameters.gl.glDetachShader(shaderParameters.shader.getId(), shaderParameters.source.getId());
                deleteShaderSource(shaderParameters.source, shaderParameters.gl);
            }
        }

        shaderParameters.gl.glDeleteProgram(shaderParameters.shader.getId());
        shaderParameters.statistics.onDeleteShader();
        shaderParameters.shader.resetObject();
    }

    public class ShaderParameters {
        Shader shader;
        ShaderSource source;
        Gl gl;
        EnumSet<Caps> caps;
        StringBuilder stringBuf;
        boolean linearizeSrgbImages;
        IntBuffer intBuf1;
        GL3 gl3;
        NativeObjectManager objManager;
        Statistics statistics;
        RenderContext context;
    }
}
