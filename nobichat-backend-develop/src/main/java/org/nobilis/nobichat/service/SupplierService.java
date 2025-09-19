package org.nobilis.nobichat.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.dto.supplier.SupplierCreateDto;
import org.nobilis.nobichat.dto.supplier.SupplierDetailDto;
import org.nobilis.nobichat.dto.supplier.SupplierListDto;
import org.nobilis.nobichat.dto.supplier.SupplierPaginatedResponseDto;
import org.nobilis.nobichat.dto.supplier.update.SupplierUpdateDto;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.mapper.SupplierMapper;
import org.nobilis.nobichat.model.Organization;
import org.nobilis.nobichat.model.Supplier;
import org.nobilis.nobichat.model.SupplierContact;
import org.nobilis.nobichat.model.SupplierEvent;
import org.nobilis.nobichat.model.SupplierFile;
import org.nobilis.nobichat.model.SupplierNomenclature;
import org.nobilis.nobichat.model.SupplierOrder;
import org.nobilis.nobichat.model.SupplierOrderItem;
import org.nobilis.nobichat.model.SupplierPrice;
import org.nobilis.nobichat.model.User;
import org.nobilis.nobichat.repository.SupplierRepository;
import org.nobilis.nobichat.repository.UserRepository;
import org.nobilis.nobichat.specification.SupplierSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SupplierPaginatedResponseDto findAllForCurrentUser(Pageable pageable, String search) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Organization currentUserOrganization = user.getOrganization();

        if (currentUserOrganization == null) {
            return new SupplierPaginatedResponseDto(0, 0, List.of());
        }

        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name").ascending().and(Sort.by("id").ascending()));
        }

        Specification<Supplier> spec = Specification
                .where(SupplierSpecifications.byOrganization(currentUserOrganization))
                .and(SupplierSpecifications.bySearchText(search));

        Page<Supplier> suppliersPage = supplierRepository.findAll(spec, pageable);

        List<SupplierListDto> dtoList = suppliersPage.getContent().stream()
                .map(supplierMapper::toSupplierListDto)
                .collect(Collectors.toList());

        return new SupplierPaginatedResponseDto(
                suppliersPage.getTotalElements(),
                suppliersPage.getTotalPages(),
                dtoList
        );
    }

    @Transactional
    public Supplier updateStatus(UUID id, Boolean active) {
        Supplier supplier = supplierRepository.findByIdWithContacts(id)
                .orElseThrow(() -> new ResourceNotFoundException("Поставщик с id: " + id + " не найден"));;
        supplier.setActive(active);
        return supplierRepository.save(supplier);
    }

    @Transactional
    public void deleteById(UUID id) {
        Supplier supplier = supplierRepository.findByIdWithContacts(id)
                .orElseThrow(() -> new ResourceNotFoundException("Поставщик с id: " + id + " не найден"));;
        supplierRepository.delete(supplier);
    }

    @Transactional(readOnly = true)
    public SupplierDetailDto getSupplierById(UUID id) {
        Supplier supplier = findSupplierAndCheckAccess(id);
        return convertEntityToDetailDto(supplier);
    }

    @Transactional
    public SupplierDetailDto createSupplier(SupplierCreateDto dto) {
        User currentUser = getCurrentUser();
        Supplier supplier = convertCreateDtoToEntity(dto);
        supplier.setOrganization(getCurrentUserOrganization());
        establishBidirectionalLinks(supplier, currentUser);
        Supplier savedSupplier = supplierRepository.save(supplier);
        return convertEntityToDetailDto(savedSupplier);
    }

    @Transactional
    public SupplierDetailDto updateSupplier(UUID id, SupplierUpdateDto dto) {
        Supplier existingSupplier = findSupplierAndCheckAccess(id);
        User currentUser = getCurrentUser();

        updateSupplierFieldsFromUpdateDto(existingSupplier, dto);

        synchronizeContacts(existingSupplier, dto.getContacts());
        synchronizeEvents(existingSupplier, dto.getEvents(), currentUser);
        synchronizeFiles(existingSupplier, dto.getFiles(), currentUser);
        synchronizeNomenclatures(existingSupplier, dto.getNomenclatures());
        synchronizePrices(existingSupplier, dto.getPrices());
        synchronizeOrders(existingSupplier, dto.getOrders(), currentUser);

        Supplier savedSupplier = supplierRepository.save(existingSupplier);
        return convertEntityToDetailDto(savedSupplier);
    }

    private void synchronizeContacts(Supplier supplier, List<SupplierUpdateDto.Contact> dtos) {
        Map<UUID, SupplierUpdateDto.Contact> dtoMap = dtos.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(SupplierUpdateDto.Contact::getId, d -> d));

        supplier.getContacts().removeIf(contact -> !dtoMap.containsKey(contact.getId()));

        supplier.getContacts().forEach(contact -> {
            SupplierUpdateDto.Contact dto = dtoMap.get(contact.getId());
            if (dto != null) {
                contact.setFullName(dto.getFullName());
                contact.setPosition(dto.getPosition());
                contact.setEmail(dto.getEmail());
                contact.setPhone(dto.getPhone());
                contact.setNotes(dto.getNotes());
                contact.setIsPrimary(dto.getIsPrimary());
            }
        });

        dtos.stream().filter(d -> d.getId() == null).forEach(newDto -> {
            SupplierContact newContact = new SupplierContact();
            newContact.setFullName(newDto.getFullName());
            newContact.setPosition(newDto.getPosition());
            newContact.setEmail(newDto.getEmail());
            newContact.setPhone(newDto.getPhone());
            newContact.setNotes(newDto.getNotes());
            newContact.setIsPrimary(newDto.getIsPrimary());
            newContact.setSupplier(supplier);
            supplier.getContacts().add(newContact);
        });
    }

    private void synchronizeEvents(Supplier supplier, List<SupplierUpdateDto.Event> dtos, User currentUser) {
        Map<UUID, SupplierUpdateDto.Event> dtoMap = dtos.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(SupplierUpdateDto.Event::getId, d -> d));

        supplier.getEvents().removeIf(event -> !dtoMap.containsKey(event.getId()));

        supplier.getEvents().forEach(event -> {
            SupplierUpdateDto.Event dto = dtoMap.get(event.getId());
            if (dto != null) {
                event.setEventType(dto.getEventType());
                event.setTitle(dto.getTitle());
                event.setDescription(dto.getDescription());
                event.setEventDate(dto.getEventDate());
                if (dto.getUserId() != null && (event.getUser() == null || !Objects.equals(event.getUser().getId(), dto.getUserId()))) {
                    event.setUser(userRepository.getReferenceById(dto.getUserId()));
                }
            }
        });

        dtos.stream().filter(d -> d.getId() == null).forEach(newDto -> {
            SupplierEvent newEvent = new SupplierEvent();
            newEvent.setEventType(newDto.getEventType());
            newEvent.setTitle(newDto.getTitle());
            newEvent.setDescription(newDto.getDescription());
            newEvent.setEventDate(newDto.getEventDate());
            newEvent.setSupplier(supplier);
            newEvent.setUser(newDto.getUserId() != null ? userRepository.getReferenceById(newDto.getUserId()) : currentUser);
            supplier.getEvents().add(newEvent);
        });
    }

    private void synchronizeFiles(Supplier supplier, List<SupplierUpdateDto.File> dtos, User currentUser) {
        Map<UUID, SupplierUpdateDto.File> dtoMap = dtos.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(SupplierUpdateDto.File::getId, d -> d));

        supplier.getFiles().removeIf(file -> !dtoMap.containsKey(file.getId()));

        supplier.getFiles().forEach(file -> {
            SupplierUpdateDto.File dto = dtoMap.get(file.getId());
            if (dto != null) {
                file.setFileName(dto.getFileName());
                file.setDescription(dto.getDescription());
                if (dto.getUploadedByUserId() != null && (file.getUploadedByUser() == null || !Objects.equals(file.getUploadedByUser().getId(), dto.getUploadedByUserId()))) {
                    file.setUploadedByUser(userRepository.getReferenceById(dto.getUploadedByUserId()));
                }
            }
        });

        dtos.stream().filter(d -> d.getId() == null).forEach(newDto -> {
            SupplierFile newFile = new SupplierFile();
            newFile.setFileName(newDto.getFileName());
            newFile.setDescription(newDto.getDescription());
            newFile.setSupplier(supplier);
            newFile.setUploadedByUser(newDto.getUploadedByUserId() != null ? userRepository.getReferenceById(newDto.getUploadedByUserId()) : currentUser);
            supplier.getFiles().add(newFile);
        });
    }

    private void synchronizeNomenclatures(Supplier supplier, List<SupplierUpdateDto.Nomenclature> dtos) {
        Map<UUID, SupplierUpdateDto.Nomenclature> dtoMap = dtos.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(SupplierUpdateDto.Nomenclature::getId, d -> d));

        supplier.getNomenclatures().removeIf(n -> !dtoMap.containsKey(n.getId()));

        supplier.getNomenclatures().forEach(n -> {
            SupplierUpdateDto.Nomenclature dto = dtoMap.get(n.getId());
            if (dto != null) {
                n.setArticleNumber(dto.getArticleNumber());
                n.setName(dto.getName());
                n.setDescription(dto.getDescription());
                n.setCategory(dto.getCategory());
                n.setUnit(dto.getUnit());
            }
        });

        dtos.stream().filter(d -> d.getId() == null).forEach(newDto -> {
            SupplierNomenclature newNomenclature = new SupplierNomenclature();
            newNomenclature.setArticleNumber(newDto.getArticleNumber());
            newNomenclature.setName(newDto.getName());
            newNomenclature.setDescription(newDto.getDescription());
            newNomenclature.setCategory(newDto.getCategory());
            newNomenclature.setUnit(newDto.getUnit());
            newNomenclature.setSupplier(supplier);
            supplier.getNomenclatures().add(newNomenclature);
        });
    }

    private void synchronizePrices(Supplier supplier, List<SupplierUpdateDto.Price> dtos) {
        Map<UUID, SupplierUpdateDto.Price> dtoMap = dtos.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(SupplierUpdateDto.Price::getId, d -> d));

        supplier.getPrices().removeIf(p -> !dtoMap.containsKey(p.getId()));

        supplier.getPrices().forEach(p -> {
            SupplierUpdateDto.Price dto = dtoMap.get(p.getId());
            if (dto != null) {
                p.setArticleNumber(dto.getArticleNumber());
                p.setName(dto.getName());
                p.setUnit(dto.getUnit());
                p.setPrice(dto.getPrice());
            }
        });

        dtos.stream().filter(d -> d.getId() == null).forEach(newDto -> {
            SupplierPrice newPrice = new SupplierPrice();
            newPrice.setArticleNumber(newDto.getArticleNumber());
            newPrice.setName(newDto.getName());
            newPrice.setUnit(newDto.getUnit());
            newPrice.setPrice(newDto.getPrice());
            newPrice.setSupplier(supplier);
            supplier.getPrices().add(newPrice);
        });
    }

    private void synchronizeOrders(Supplier supplier, List<SupplierUpdateDto.Order> dtos, User currentUser) {
        Map<UUID, SupplierUpdateDto.Order> dtoMap = dtos.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(SupplierUpdateDto.Order::getId, d -> d));

        supplier.getOrders().removeIf(order -> !dtoMap.containsKey(order.getId()));

        supplier.getOrders().forEach(order -> {
            SupplierUpdateDto.Order dto = dtoMap.get(order.getId());
            if (dto != null) {
                order.setOrderNumber(dto.getOrderNumber());
                order.setStatus(dto.getStatus());
                order.setTotalAmount(dto.getTotalAmount());
                order.setOrderDate(dto.getOrderDate());
                synchronizeOrderItems(order, dto.getItems());
            }
        });

        dtos.stream().filter(d -> d.getId() == null).forEach(newDto -> {
            SupplierOrder newOrder = new SupplierOrder();
            newOrder.setOrderNumber(newDto.getOrderNumber());
            newOrder.setStatus(newDto.getStatus());
            newOrder.setTotalAmount(newDto.getTotalAmount());
            newOrder.setOrderDate(newDto.getOrderDate());
            newOrder.setSupplier(supplier);
            newOrder.setOrganization(supplier.getOrganization());
            newOrder.setUser(currentUser);

            newDto.getItems().forEach(itemDto -> {
                SupplierOrderItem newItem = new SupplierOrderItem();
                newItem.setArticleNumber(itemDto.getArticleNumber());
                newItem.setName(itemDto.getName());
                newItem.setUnit(itemDto.getUnit());
                newItem.setQuantity(itemDto.getQuantity());
                newItem.setPricePerUnit(itemDto.getPricePerUnit());
                newItem.setLineTotal(itemDto.getLineTotal());
                newItem.setOrder(newOrder);
                newOrder.getItems().add(newItem);
            });
            supplier.getOrders().add(newOrder);
        });
    }

    private void synchronizeOrderItems(SupplierOrder order, List<SupplierUpdateDto.OrderItem> dtos) {
        Map<UUID, SupplierUpdateDto.OrderItem> dtoMap = dtos.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(SupplierUpdateDto.OrderItem::getId, d -> d));

        order.getItems().removeIf(item -> !dtoMap.containsKey(item.getId()));

        order.getItems().forEach(item -> {
            SupplierUpdateDto.OrderItem dto = dtoMap.get(item.getId());
            if (dto != null) {
                item.setArticleNumber(dto.getArticleNumber());
                item.setName(dto.getName());
                item.setUnit(dto.getUnit());
                item.setQuantity(dto.getQuantity());
                item.setPricePerUnit(dto.getPricePerUnit());
                item.setLineTotal(dto.getLineTotal());
            }
        });

        dtos.stream().filter(d -> d.getId() == null).forEach(newDto -> {
            SupplierOrderItem newItem = new SupplierOrderItem();
            newItem.setArticleNumber(newDto.getArticleNumber());
            newItem.setName(newDto.getName());
            newItem.setUnit(newDto.getUnit());
            newItem.setQuantity(newDto.getQuantity());
            newItem.setPricePerUnit(newDto.getPricePerUnit());
            newItem.setLineTotal(newDto.getLineTotal());
            newItem.setOrder(order);
            order.getItems().add(newItem);
        });
    }

    private void updateSupplierFieldsFromUpdateDto(Supplier supplier, SupplierUpdateDto dto) {
        supplier.setName(dto.getName());
        supplier.setDescription(dto.getDescription());
        supplier.setSupplierCode(dto.getSupplierCode());
        supplier.setDirectorName(dto.getDirectorName());
        supplier.setActive(dto.getActive());
        supplier.setInn(dto.getInn());
        supplier.setContractNumber(dto.getContractNumber());
        supplier.setLegalAddress(dto.getLegalAddress());
        supplier.setKpp(dto.getKpp());
        supplier.setOgrn(dto.getOgrn());
        supplier.setOkpo(dto.getOkpo());
        supplier.setBankName(dto.getBankName());
        supplier.setCorrespondentAccount(dto.getCorrespondentAccount());
    }

    private SupplierDetailDto convertEntityToDetailDto(Supplier supplier) {
        SupplierDetailDto dto = new SupplierDetailDto();
        dto.setId(supplier.getId());
        dto.setName(supplier.getName());
        dto.setDescription(supplier.getDescription());
        dto.setSupplierCode(supplier.getSupplierCode());
        dto.setDirectorName(supplier.getDirectorName());
        dto.setActive(supplier.getActive());
        dto.setInn(supplier.getInn());
        dto.setContractNumber(supplier.getContractNumber());
        dto.setLegalAddress(supplier.getLegalAddress());
        dto.setKpp(supplier.getKpp());
        dto.setOgrn(supplier.getOgrn());
        dto.setOkpo(supplier.getOkpo());
        dto.setBankName(supplier.getBankName());
        dto.setCorrespondentAccount(supplier.getCorrespondentAccount());
        dto.setIconUrl(String.format("/images/%s/%s", EntityType.supplier, supplier.getId()));

        dto.setContacts(supplier.getContacts().stream().map(c -> {
            SupplierDetailDto.Contact cDto = new SupplierDetailDto.Contact();
            cDto.setId(c.getId());
            cDto.setFullName(c.getFullName());
            cDto.setPosition(c.getPosition());
            cDto.setEmail(c.getEmail());
            cDto.setPhone(c.getPhone());
            cDto.setNotes(c.getNotes());
            cDto.setIsPrimary(c.getIsPrimary());
            return cDto;
        }).collect(Collectors.toList()));

        dto.setEvents(supplier.getEvents().stream().map(e -> {
            SupplierDetailDto.Event eDto = new SupplierDetailDto.Event();
            eDto.setId(e.getId());
            eDto.setUserId(e.getUser() != null ? e.getUser().getId() : null);
            eDto.setEventType(e.getEventType());
            eDto.setTitle(e.getTitle());
            eDto.setDescription(e.getDescription());
            eDto.setEventDate(e.getEventDate());
            return eDto;
        }).collect(Collectors.toList()));

        dto.setFiles(supplier.getFiles().stream().map(f -> {
            SupplierDetailDto.File fDto = new SupplierDetailDto.File();
            fDto.setId(f.getId());
            fDto.setUploadedByUserId(f.getUploadedByUser() != null ? f.getUploadedByUser().getId() : null);
            fDto.setFileName(f.getFileName());
            fDto.setDescription(f.getDescription());
            return fDto;
        }).collect(Collectors.toList()));

        dto.setNomenclatures(supplier.getNomenclatures().stream().map(n -> {
            SupplierDetailDto.Nomenclature nDto = new SupplierDetailDto.Nomenclature();
            nDto.setId(n.getId());
            nDto.setArticleNumber(n.getArticleNumber());
            nDto.setName(n.getName());
            nDto.setDescription(n.getDescription());
            nDto.setCategory(n.getCategory());
            nDto.setUnit(n.getUnit());
            return nDto;
        }).collect(Collectors.toList()));

        dto.setPrices(supplier.getPrices().stream().map(p -> {
            SupplierDetailDto.Price pDto = new SupplierDetailDto.Price();
            pDto.setId(p.getId());
            pDto.setArticleNumber(p.getArticleNumber());
            pDto.setName(p.getName());
            pDto.setUnit(p.getUnit());
            pDto.setPrice(p.getPrice());
            return pDto;
        }).collect(Collectors.toList()));

        dto.setOrders(supplier.getOrders().stream().map(o -> {
            SupplierDetailDto.Order oDto = new SupplierDetailDto.Order();
            oDto.setId(o.getId());
            oDto.setOrderNumber(o.getOrderNumber());
            oDto.setStatus(o.getStatus());
            oDto.setTotalAmount(o.getTotalAmount());
            oDto.setOrderDate(o.getOrderDate());
            oDto.setItems(o.getItems().stream().map(i -> {
                SupplierDetailDto.OrderItem iDto = new SupplierDetailDto.OrderItem();
                iDto.setId(i.getId());
                iDto.setArticleNumber(i.getArticleNumber());
                iDto.setName(i.getName());
                iDto.setUnit(i.getUnit());
                iDto.setQuantity(i.getQuantity());
                iDto.setPricePerUnit(i.getPricePerUnit());
                iDto.setLineTotal(i.getLineTotal());
                return iDto;
            }).collect(Collectors.toList()));
            return oDto;
        }).collect(Collectors.toList()));

        return dto;
    }

    private Supplier convertCreateDtoToEntity(SupplierCreateDto dto) {
        Supplier supplier = new Supplier();
        supplier.setName(dto.getName());
        supplier.setDescription(dto.getDescription());
        supplier.setSupplierCode(dto.getSupplierCode());
        supplier.setDirectorName(dto.getDirectorName());
        supplier.setActive(dto.getActive());
        supplier.setInn(dto.getInn());
        supplier.setContractNumber(dto.getContractNumber());
        supplier.setLegalAddress(dto.getLegalAddress());
        supplier.setKpp(dto.getKpp());
        supplier.setOgrn(dto.getOgrn());
        supplier.setOkpo(dto.getOkpo());
        supplier.setBankName(dto.getBankName());
        supplier.setCorrespondentAccount(dto.getCorrespondentAccount());

        supplier.setContacts(dto.getContacts().stream().map(cDto -> {
            SupplierContact c = new SupplierContact();
            c.setFullName(cDto.getFullName());
            c.setPosition(cDto.getPosition());
            c.setEmail(cDto.getEmail());
            c.setPhone(cDto.getPhone());
            c.setNotes(cDto.getNotes());
            c.setIsPrimary(cDto.getIsPrimary());
            return c;
        }).collect(Collectors.toList()));

        supplier.setEvents(dto.getEvents().stream().map(eDto -> {
            SupplierEvent e = new SupplierEvent();
            e.setEventType(eDto.getEventType());
            e.setTitle(eDto.getTitle());
            e.setDescription(eDto.getDescription());
            e.setEventDate(eDto.getEventDate());
            return e;
        }).collect(Collectors.toList()));

        supplier.setFiles(dto.getFiles().stream().map(fDto -> {
            SupplierFile f = new SupplierFile();
            f.setFileName(fDto.getFileName());
            f.setDescription(fDto.getDescription());
            return f;
        }).collect(Collectors.toList()));

        supplier.setNomenclatures(dto.getNomenclatures().stream().map(nDto -> {
            SupplierNomenclature n = new SupplierNomenclature();
            n.setArticleNumber(nDto.getArticleNumber());
            n.setName(nDto.getName());
            n.setDescription(nDto.getDescription());
            n.setCategory(nDto.getCategory());
            n.setUnit(nDto.getUnit());
            return n;
        }).collect(Collectors.toList()));

        supplier.setPrices(dto.getPrices().stream().map(pDto -> {
            SupplierPrice p = new SupplierPrice();
            p.setArticleNumber(pDto.getArticleNumber());
            p.setName(pDto.getName());
            p.setUnit(pDto.getUnit());
            p.setPrice(pDto.getPrice());
            return p;
        }).collect(Collectors.toList()));

        supplier.setOrders(dto.getOrders().stream().map(oDto -> {
            SupplierOrder o = new SupplierOrder();
            o.setOrderNumber(oDto.getOrderNumber());
            o.setStatus(oDto.getStatus());
            o.setTotalAmount(oDto.getTotalAmount());
            o.setOrderDate(oDto.getOrderDate());
            o.setItems(oDto.getItems().stream().map(iDto -> {
                SupplierOrderItem i = new SupplierOrderItem();
                i.setArticleNumber(iDto.getArticleNumber());
                i.setName(iDto.getName());
                i.setUnit(iDto.getUnit());
                i.setQuantity(iDto.getQuantity());
                i.setPricePerUnit(iDto.getPricePerUnit());
                i.setLineTotal(iDto.getLineTotal());
                return i;
            }).collect(Collectors.toList()));
            return o;
        }).collect(Collectors.toList()));

        return supplier;
    }

    private void establishBidirectionalLinks(Supplier supplier, User user) {
        supplier.getContacts().forEach(c -> c.setSupplier(supplier));
        supplier.getNomenclatures().forEach(n -> n.setSupplier(supplier));
        supplier.getPrices().forEach(p -> p.setSupplier(supplier));

        List<SupplierCreateDto.Event> eventDtos = supplier.getEvents().stream().map(e -> {
            SupplierCreateDto.Event eventDto = new SupplierCreateDto.Event();
            eventDto.setUserId(e.getUser() != null ? e.getUser().getId() : null);
            return eventDto;
        }).collect(Collectors.toList());

        for (int i = 0; i < supplier.getEvents().size(); i++) {
            SupplierEvent event = supplier.getEvents().get(i);
            SupplierCreateDto.Event eventDto = eventDtos.get(i);
            event.setSupplier(supplier);
            event.setUser(eventDto.getUserId() != null ? userRepository.getReferenceById(eventDto.getUserId()) : user);
        }

        List<SupplierCreateDto.File> fileDtos = supplier.getFiles().stream().map(f -> {
            SupplierCreateDto.File fileDto = new SupplierCreateDto.File();
            fileDto.setUploadedByUserId(f.getUploadedByUser() != null ? f.getUploadedByUser().getId() : null);
            return fileDto;
        }).collect(Collectors.toList());

        for (int i = 0; i < supplier.getFiles().size(); i++) {
            SupplierFile file = supplier.getFiles().get(i);
            SupplierCreateDto.File fileDto = fileDtos.get(i);
            file.setSupplier(supplier);
            file.setUploadedByUser(fileDto.getUploadedByUserId() != null ? userRepository.getReferenceById(fileDto.getUploadedByUserId()) : user);
        }

        supplier.getOrders().forEach(o -> {
            o.setSupplier(supplier);
            o.setOrganization(supplier.getOrganization());
            o.setUser(user);
            o.getItems().forEach(i -> i.setOrder(o));
        });
    }

    private Supplier findSupplierAndCheckAccess(UUID id) {
        Organization org = getCurrentUserOrganization();
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found with id: " + id));

        if (supplier.getOrganization() == null || !Objects.equals(supplier.getOrganization().getId(), org.getId())) {
            throw new AccessDeniedException("Access to supplier " + id + " is denied.");
        }

        Hibernate.initialize(supplier.getContacts());
        Hibernate.initialize(supplier.getEvents());
        Hibernate.initialize(supplier.getFiles());
        Hibernate.initialize(supplier.getNomenclatures());
        Hibernate.initialize(supplier.getPrices());
        if (supplier.getOrders() != null) {
            supplier.getOrders().forEach(order -> Hibernate.initialize(order.getItems()));
        }

        return supplier;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private Organization getCurrentUserOrganization() {
        User user = getCurrentUser();
        if (user.getOrganization() == null) {
            throw new IllegalStateException("Current user does not belong to any organization.");
        }
        return user.getOrganization();
    }
}