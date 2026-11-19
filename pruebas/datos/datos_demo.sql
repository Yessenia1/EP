-- Datos de demostración de Salud Total SAC para una base recién creada por Flyway.
-- Requiere que el backend haya arrancado una vez (crea nivel FARMACIA, sedes y almacenes desde la semilla).
BEGIN;

INSERT INTO catalogo (id, codigo, nombre, nombre_catalogo, tipo, tipo_catalogo, maneja_lotes, id_nivel, estado,
                      precio_compra, precio_venta_unitario, created_at, usuario_creacion)
OVERRIDING SYSTEM VALUE
VALUES (1, 'MED001', 'PARACETAMOL 500MG TAB',  'PARACETAMOL 500MG TAB',  'PRODUCTO', 'MEDICAMENTO', true, 1, 1, 2.50, 8.00,  now(), 'demo'),
       (2, 'MED002', 'AMOXICILINA 500MG CAP',  'AMOXICILINA 500MG CAP',  'PRODUCTO', 'MEDICAMENTO', true, 1, 1, 5.00, 15.00, now(), 'demo'),
       (3, 'MED003', 'IBUPROFENO 400MG TAB',   'IBUPROFENO 400MG TAB',   'PRODUCTO', 'MEDICAMENTO', true, 1, 1, 3.00, 10.00, now(), 'demo'),
       (4, 'MED004', 'AZITROMICINA 500MG TAB', 'AZITROMICINA 500MG TAB', 'PRODUCTO', 'MEDICAMENTO', true, 1, 1, 8.00, 22.00, now(), 'demo'),
       (5, 'MED005', 'CETIRIZINA 10MG TAB',    'CETIRIZINA 10MG TAB',    'PRODUCTO', 'MEDICAMENTO', true, 1, 1, 1.50, 6.00,  now(), 'demo'),
       (6, 'MED006', 'OMEPRAZOL 20MG CAP',     'OMEPRAZOL 20MG CAP',     'PRODUCTO', 'MEDICAMENTO', true, 1, 1, 3.50, 12.00, now(), 'demo')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('catalogo', 'id'), (SELECT max(id) FROM catalogo));

INSERT INTO productos (id_catalogo, id_sucursal, id_almacen, nro_lote, fecha_venc, stock, stock_minimo, stock_maximo,
                       precio_compra, precio_venta_unitario, dias_alerta_vencimiento, fecha_reg, usuario_crea)
SELECT v.cat, v.suc, (SELECT min(id) FROM almacen WHERE id_sucursal = v.suc), v.lote, v.venc, v.stock, v.minimo, v.maximo,
       c.precio_compra, c.precio_venta_unitario, 90, now(), 'demo'
  FROM (VALUES
        (1, 1, 'L-2105', current_date + 40, 80::numeric,  100::numeric, 600::numeric),
        (1, 1, 'L-2291', current_date + 55, 500::numeric, 100::numeric, 600::numeric),
        (2, 1, 'L-1180', current_date + 25, 300::numeric, 60::numeric,  300::numeric),
        (3, 1, 'L-0444', current_date + 280, 450::numeric, 500::numeric, 900::numeric),
        (4, 1, 'L-3310', current_date + 70, 34::numeric,  40::numeric,  250::numeric),
        (5, 1, 'L-7713', current_date + 15, 400::numeric, 30::numeric,  200::numeric),
        (6, 1, 'L-0932', current_date + 100, 60::numeric, 80::numeric,  600::numeric),
        (1, 2, 'L-2290', current_date + 55, 140::numeric, 60::numeric,  300::numeric),
        (2, 2, 'L-1181', current_date + 20, 18::numeric,  60::numeric,  120::numeric),
        (5, 2, 'L-7714', current_date - 5,  12::numeric,  30::numeric,  150::numeric)
       ) AS v(cat, suc, lote, venc, stock, minimo, maximo)
  JOIN catalogo c ON c.id = v.cat
 WHERE NOT EXISTS (SELECT 1 FROM productos p WHERE p.nro_lote = v.lote AND p.id_sucursal = v.suc);

COMMIT;

SELECT s.nombre AS sede, c.nombre_catalogo AS producto, p.id_producto, p.nro_lote, p.fecha_venc, p.stock, p.stock_minimo
  FROM productos p JOIN catalogo c ON c.id = p.id_catalogo JOIN sucursal s ON s.id_sucursal = p.id_sucursal
 ORDER BY s.id_sucursal, c.nombre_catalogo, p.fecha_venc;
