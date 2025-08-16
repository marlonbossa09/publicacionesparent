package com.publicaciones.controller;

/**
 *
 * @author Bossa
 */
import com.publicaciones.model.PublicacionRequest;
import com.publicaciones.model.EstadoPublicacion;
import com.publicaciones.model.EstadoRequest;
import com.publicaciones.model.Publicacion;
import com.publicaciones.service.PublicacionService;
import com.publicaciones.service.S3Service;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

//IMPORTACIONES SWAGGER
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/publicaciones")
@Tag(name = "Publicaciones", description = "Endpoints para publicaciones")
public class PublicacionController {

    private final PublicacionService service;
    private final S3Service s3Service;
    
    
    public PublicacionController(PublicacionService service, S3Service s3Service) {
        this.service = service;
        this.s3Service = s3Service;
    }
    
    //Obtener pblicaciones
    @Operation(
        summary = "Listar publicaciones",
        description = "Devuelve todas las publicaciones disponibles"
    )
    @ApiResponse(responseCode = "200", description = "Lista de publicaciones obtenida exitosamente")
    @GetMapping
    public List<Publicacion> listar() {
        return service.listar();
    }

     @Operation(summary = "Obtener publicación por ID")
    @ApiResponse(responseCode = "200", description = "Publicación encontrada")
    @ApiResponse(responseCode = "404", description = "Publicación no encontrada")
    //Obtener pblicaciones por id
    @GetMapping("/{id}")
    public ResponseEntity<Publicacion> obtenerPorId(@PathVariable("id") Long id) {
        return service.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    

    /* @PostMapping
    public Publicacion crear(@RequestBody Publicacion publicacion) {
        return service.guardar(publicacion);
    } */
    
 
    // CREAR PUBLICACION, JSON SE DEBE MANDAR EN BASE 64 LA IMAGEN
     @Operation(
        summary = "Crear publicación",
        description = "Crea una nueva publicación a partir de la descripción y una imagen en Base64"
    )
    @ApiResponse(responseCode = "200", description = "Publicación creada exitosamente")
    @PostMapping("/crear-publicacion")
    public ResponseEntity<Publicacion> crearDesdeBinario(
       @RequestBody PublicacionRequest request
            ) throws IOException {
        
        // OBLIGADO A QUE EL ESTADO SEA PENDIENTE
        Publicacion publicacion = new Publicacion();
        publicacion.setDescripcion(request.getDescripcion());
        publicacion.setEstado(EstadoPublicacion.PENDIENTE);
        publicacion = service.guardar(publicacion);

        // CODIFICANDO BASE 64 A BYTE
        byte[] imagenBytes = java.util.Base64.getDecoder().decode(request.getImagenBase64());

        // SUBIR BINARIO A S3 CON SU ID
        String urlS3 = s3Service.subirArchivo(
                "publicaciones/" + publicacion.getId() + ".png",
                imagenBytes
        );

        // ACTUALIZAR CON EL LINK DE S3
        publicacion.setImagenUrl(urlS3);
        publicacion = service.guardar(publicacion);

        return ResponseEntity.ok(publicacion);
    }
    
    // EDITAR UNA PUBLICACIÓN EN GENERAL
    @Operation(summary = "Actualizar publicación")
    @ApiResponse(responseCode = "200", description = "Publicación actualizada correctamente")
    @ApiResponse(responseCode = "404", description = "Publicación no encontrada")
    @PutMapping("/{id}")
    public ResponseEntity<Publicacion> actualizar(@PathVariable("id") Long id, @RequestBody Publicacion publicacion) {
        return service.buscarPorId(id)
                .map(p -> {
                    p.setDescripcion(publicacion.getDescripcion());
                    p.setImagenUrl(publicacion.getImagenUrl());
                    p.setEstado(publicacion.getEstado());
                    return ResponseEntity.ok(service.guardar(p));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    
    // EDITAR EL ESTADO DE UNA PUBLCIACIÓN
     @Operation(summary = "Cambiar estado de publicación")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @PostMapping("/{id}/estado")
    public ResponseEntity<Publicacion> cambiarEstado(
            @PathVariable("id") Long id,
            @RequestBody EstadoRequest request) {
        try {
            EstadoPublicacion estado = EstadoPublicacion.valueOf(request.getEstado().toUpperCase());
            return service.buscarPorId(id)
                    .map(p -> {
                        p.setEstado(estado);
                        return ResponseEntity.ok(service.guardar(p));
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    //OBTENER UNA PUBLICACION POR EL ESTADO
    @Operation(summary = "Listar publicaciones por estado")
    @GetMapping("/estado")
    public ResponseEntity<List<Publicacion>> listarPorEstado(
            @RequestParam("estado") String estadoStr,
            @RequestParam(value = "limit", required = false) Integer limit) {

        try {
            EstadoPublicacion estado = EstadoPublicacion.valueOf(estadoStr.toUpperCase());
            List<Publicacion> publicaciones = service.buscarPorEstado(estado);

            
            if (limit != null && limit > 0 && limit < publicaciones.size()) {
                publicaciones = publicaciones.subList(0, limit);
            }

            return ResponseEntity.ok(publicaciones);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
     
    @Operation(summary = "Eliminar publicación")
    @ApiResponse(responseCode = "204", description = "Publicación eliminada correctamente")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
       }
}
